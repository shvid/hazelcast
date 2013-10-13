package com.hazelcast.map.proxy;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.hazelcast.config.DistributionStrategyConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.IReplicatedMap;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.MapService;
import com.hazelcast.map.RecordStore;
import com.hazelcast.map.record.Record;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.partition.PartitionService;
import com.hazelcast.partition.PartitionView;
import com.hazelcast.query.Predicate;
import com.hazelcast.spi.NodeEngine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReplicatedMapProxyImpl<K, V>
        extends MapProxyImpl<K, V> implements IReplicatedMap<K, V> {

    private final DistributionStrategyConfig distributionStrategyConfig;

    private final PartitionService partitionService;

    private final NodeEngine nodeEngine;

    public ReplicatedMapProxyImpl(String name, MapService service, NodeEngine nodeEngine) {
        super(name, service, nodeEngine);
        MapConfig config = service.getMapContainer(name).getMapConfig();
        this.distributionStrategyConfig = config.getDistributionStrategyConfig();
        this.partitionService = nodeEngine.getPartitionService();
        this.nodeEngine = nodeEngine;
    }

    @Override
    public V get(Object k) {
        Data keyData = getService().toData(k, partitionStrategy);
        int partitionId = partitionService.getPartitionId(keyData);
        return getValueInternal((K) k, keyData, partitionId);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        if (distributionStrategyConfig == DistributionStrategyConfig.Distributed) {
            Map<Data, K> ks = new HashMap<Data, K>(keys.size());
            for (K key : keys) {
                if (key == null) {
                    throw new NullPointerException(NULL_KEY_IS_NOT_ALLOWED);
                }
                Data keyData = getService().toData(key, partitionStrategy);
                ks.put(keyData, key);
            }
            Map<K, V> map = new HashMap<K, V>(ks.size());
            for (Entry<Data, K> entry : ks.entrySet()) {
                int partitionId = partitionService.getPartitionId(entry.getKey());
                V v = getValueInternal(entry.getValue(), entry.getKey(), partitionId);
                map.put(entry.getValue(), v);
            }
            return map;
        }
        return super.getAll(keys);
    }

    @Override
    public Future<V> getAsync(final K k) {
        if (distributionStrategyConfig == DistributionStrategyConfig.Distributed) {
            return new Future<V>() {
                private volatile boolean cancelled = false;

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    this.cancelled = true;
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }

                @Override
                public boolean isDone() {
                    return !cancelled;
                }

                @Override
                public V get() throws InterruptedException, ExecutionException {
                    Data keyData = getService().toData(k, partitionStrategy);
                    int partitionId = partitionService.getPartitionId(keyData);
                    return getValueInternal(k, keyData, partitionId);
                }

                @Override
                public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    return get();
                }
            };
        }
        return super.getAsync(k);
    }

    @Override
    public EntryView<K, V> getEntryView(K key) {
        return super.getEntryView(key);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return super.entrySet();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<Entry<K, V>> entrySet(Predicate predicate) {
        return super.entrySet(predicate);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean containsKey(Object k) {
        return super.containsKey(k);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean containsValue(Object v) {
        return super.containsValue(v);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Object executeOnKey(K key, EntryProcessor entryProcessor) {
        return super.executeOnKey(key, entryProcessor);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<K> keySet() {
        return super.keySet();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<K> keySet(Predicate predicate) {
        return super.keySet(predicate);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<K> localKeySet() {
        return super.localKeySet();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<K> localKeySet(Predicate predicate) {
        return super.localKeySet(predicate);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Collection<V> values() {
        return super.values();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Collection<V> values(Predicate predicate) {
        return super.values(predicate);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean isEventuallyConsistent() {
        return distributionStrategyConfig == DistributionStrategyConfig.Distributed;
    }

    protected V findValueInBackup(Data keyData, int partitionId) {
        PartitionView partitionView = partitionService.getPartition(partitionId);
        Address address = nodeEngine.getThisAddress();
        if (partitionView.getReplicaIndexOf(address) != -1) {
            RecordStore recordStore = getService().getRecordStore(partitionId, name);
            V value = (V) recordStore.get(keyData);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected V findInLocalPartitions(Data keyData, int partitionId) {
        if (nodeEngine.getThisAddress().equals(partitionService.getPartitionOwner(partitionId))) {
            RecordStore recordStore = getService().getRecordStore(partitionId, name);
            Record record = recordStore.getRecord(keyData);
            if (record != null) {
                return (V) record.getValue();
            }
        }
        return null;
    }

    protected void storeToLocalRecordStore(Data keyData, V value, int partitionId) {
        RecordStore recordStore = getService().getRecordStore(partitionId, name);
        int ttl = mapConfig.getTimeToLiveSeconds();
        recordStore.put(keyData, getService().toData(value), getTimeInMillis(ttl, TimeUnit.SECONDS));
    }

    protected V getValueInternal(K key, Data keyData, int partitionId) {
        V value = findInLocalPartitions(keyData, partitionId);
        if (value != null) {
            return value;
        }
        value = findValueInBackup(keyData, partitionId);
        if (value != null) {
            return value;
        }
        if (distributionStrategyConfig == DistributionStrategyConfig.Replicated) {
            value = super.get(key);
            if (value != null) {
                storeToLocalRecordStore(keyData, value, partitionId);
            }
        }
        return null;
    }

}
