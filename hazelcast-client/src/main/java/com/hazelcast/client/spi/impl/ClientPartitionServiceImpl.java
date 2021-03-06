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

package com.hazelcast.client.spi.impl;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.spi.ClientClusterService;
import com.hazelcast.client.spi.ClientPartitionService;
import com.hazelcast.core.Member;
import com.hazelcast.core.Partition;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.partition.client.GetPartitionsRequest;
import com.hazelcast.partition.client.PartitionsResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @mdogan 5/16/13
 */
public final class ClientPartitionServiceImpl implements ClientPartitionService {

    private final HazelcastClient client;

    private volatile int partitionCount;

    private ConcurrentHashMap<Integer, Address> partitions;

    public ClientPartitionServiceImpl(HazelcastClient client) {
        this.client = client;
    }

    public void start() {
        getInitialPartitions();
        client.getClientExecutionService().scheduleWithFixedDelay(new Runnable() {
            public void run() {
                final ClientClusterService clusterService = client.getClientClusterService();
                final Address master = clusterService.getMasterAddress();
                final PartitionsResponse response = getPartitionsFrom((ClientClusterServiceImpl) clusterService, master);
                if (response != null) {
                    processPartitionResponse(response);
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public void refreshPartitions() {
        client.getClientExecutionService().execute(new Runnable() {
            public void run() {
                final ClientClusterService clusterService = client.getClientClusterService();
                final Address master = clusterService.getMasterAddress();
                final PartitionsResponse response = getPartitionsFrom((ClientClusterServiceImpl) clusterService, master);
                if (response != null) {
                    processPartitionResponse(response);
                }
            }
        });
    }

    private void getInitialPartitions() {
        final ClientClusterService clusterService = client.getClientClusterService();
        final Collection<MemberImpl> memberList = clusterService.getMemberList();
        for (MemberImpl member : memberList) {
            final Address target = member.getAddress();
            PartitionsResponse response = getPartitionsFrom((ClientClusterServiceImpl) clusterService, target);
            if (response != null) {
                processPartitionResponse(response);
                return;
            }
        }
        throw new IllegalStateException("Cannot get initial partitions!");
    }

    private PartitionsResponse getPartitionsFrom(ClientClusterServiceImpl clusterService, Address address) {
        try {
            return clusterService.sendAndReceive(address, new GetPartitionsRequest());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void processPartitionResponse(PartitionsResponse response) {
        final Address[] members = response.getMembers();
        final int[] ownerIndexes = response.getOwnerIndexes();
        if (partitionCount == 0) {
            partitions = new ConcurrentHashMap<Integer, Address>(271, 0.75f, 1);
            partitionCount = ownerIndexes.length;
        }
        for (int partitionId = 0; partitionId < partitionCount; partitionId++) {
            final int ownerIndex = ownerIndexes[partitionId];
            if (ownerIndex > -1) {
                partitions.put(partitionId, members[ownerIndex]);
            }
        }
    }

    public void stop() {

    }

    @Override
    public Address getPartitionOwner(int partitionId) {
        return partitions.get(partitionId);
    }

    @Override
    public int getPartitionId(Data key) {
        final int pc = partitionCount;
        int hash = key.getPartitionHash();
        return (hash == Integer.MIN_VALUE) ? 0 : Math.abs(hash) % pc;
    }

    @Override
    public int getPartitionId(Object key) {
        final Data data = client.getSerializationService().toData(key);
        return getPartitionId(data);
    }

    @Override
    public int getPartitionCount() {
        return partitionCount;
    }

    @Override
    public Partition getPartition(int partitionId) {
        return new PartitionImpl(partitionId);
    }

    private class PartitionImpl implements Partition {

        private final int partitionId;

        private PartitionImpl(int partitionId) {
            this.partitionId = partitionId;
        }

        public int getPartitionId() {
            return partitionId;
        }

        public Member getOwner() {
            final Address owner = getPartitionOwner(partitionId);
            if (owner != null) {
                return client.getClientClusterService().getMember(owner);
            }
            return null;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PartitionImpl{");
            sb.append("partitionId=").append(partitionId);
            sb.append('}');
            return sb.toString();
        }
    }
}
