/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.instance;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.DuplicateInstanceNameException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import com.hazelcast.jmx.ManagementService;
import com.hazelcast.spi.annotation.PrivateApi;
import com.hazelcast.util.ExceptionUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static com.hazelcast.core.LifecycleEvent.LifecycleState.STARTED;

@SuppressWarnings("SynchronizationOnStaticField")
@PrivateApi
public final class HazelcastInstanceFactory {

    private static final ConcurrentMap<String, HazelcastInstanceProxy> INSTANCE_MAP
            = new ConcurrentHashMap<String, HazelcastInstanceProxy>(5);

    private static final AtomicInteger factoryIdGen = new AtomicInteger();

    private static final Object INIT_LOCK = new Object();

    public static Set<HazelcastInstance> getAllHazelcastInstances() {
        return new HashSet<HazelcastInstance>(INSTANCE_MAP.values());
    }

    public static HazelcastInstance getHazelcastInstance(String instanceName) {
        return INSTANCE_MAP.get(instanceName);
    }

    public static HazelcastInstance newHazelcastInstance(Config config) {
        if (config == null) {
            config = new XmlConfigBuilder().build();
        }
        String name = config.getInstanceName();
        if (name == null || name.trim().length() == 0) {
            name = createInstanceName(config);
            return newHazelcastInstance(config, name, new DefaultNodeContext());
        } else {
            synchronized (INIT_LOCK) {
                if (INSTANCE_MAP.containsKey(name)) {
                    throw new DuplicateInstanceNameException("HazelcastInstance with name '" + name + "' already exists!");
                }
                factoryIdGen.incrementAndGet();
                return newHazelcastInstance(config, name, new DefaultNodeContext());
            }
        }
    }

    private static String createInstanceName(Config config) {
        return "_hzInstance_" + factoryIdGen.incrementAndGet() + "_" + config.getGroupConfig().getName();
    }

    public static HazelcastInstance newHazelcastInstance(Config config, String instanceName, NodeContext nodeContext) {
        if (instanceName == null || instanceName.trim().length() == 0) {
            instanceName = createInstanceName(config);
        }
        HazelcastInstanceProxy proxy;
        try {
            final HazelcastInstanceImpl hazelcastInstance = new HazelcastInstanceImpl(instanceName, config, nodeContext);
            OutOfMemoryErrorDispatcher.register(hazelcastInstance);
            proxy = new HazelcastInstanceProxy(hazelcastInstance);
            INSTANCE_MAP.put(instanceName, proxy);
            final Node node = hazelcastInstance.node;
            final Iterator<Member> iter = node.getClusterService().getMembers().iterator();
            final boolean firstMember = (iter.hasNext() && iter.next().localMember());
            final int initialWaitSeconds = node.groupProperties.INITIAL_WAIT_SECONDS.getInteger();
            if (initialWaitSeconds > 0) {
                try {
                    Thread.sleep(initialWaitSeconds * 1000);
                    if (firstMember) {
                        node.partitionService.firstArrangement();
                    } else {
                        Thread.sleep(4 * 1000);
                    }
                } catch (InterruptedException ignored) {
                }
            }
            final int initialMinClusterSize = node.groupProperties.INITIAL_MIN_CLUSTER_SIZE.getInteger();
            while (node.getClusterService().getSize() < initialMinClusterSize) {
                try {
                    hazelcastInstance.logger.log(Level.INFO, "HazelcastInstance waiting for cluster size of " + initialMinClusterSize);
                    //noinspection BusyWait
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            if (initialMinClusterSize > 0) {
                if (firstMember) {
                    node.partitionService.firstArrangement();
                } else {
                    Thread.sleep(3 * 1000);
                }
            }
            hazelcastInstance.lifecycleService.fireLifecycleEvent(STARTED);
        } catch (Throwable t) {
            throw ExceptionUtil.rethrow(t);
        }
        return proxy;
    }

    public static void shutdownAll() {
        final List<HazelcastInstanceProxy> instances = new ArrayList<HazelcastInstanceProxy>(INSTANCE_MAP.values());
        INSTANCE_MAP.clear();
        Collections.sort(instances, new Comparator<HazelcastInstanceProxy>() {
            public int compare(HazelcastInstanceProxy o1, HazelcastInstanceProxy o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (HazelcastInstanceProxy proxy : instances) {
            proxy.getLifecycleService().shutdown();
            proxy.original = null;
        }
        ManagementService.shutdownAll();
    }

    static void remove(HazelcastInstanceImpl instance) {
        OutOfMemoryErrorDispatcher.deregister(instance);
        final HazelcastInstanceProxy proxy = INSTANCE_MAP.remove(instance.getName());
        if (proxy != null) {
            proxy.original = null;
        }
        if (INSTANCE_MAP.size() == 0) {
            ManagementService.shutdownAll();
        }
    }
}
