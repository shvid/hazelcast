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

package com.hazelcast.map.operation;

import com.hazelcast.config.DistributionStrategyConfig;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.core.Member;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.map.ReplicatedMapConfigAdapter;
import com.hazelcast.map.SimpleEntryView;
import com.hazelcast.map.record.Record;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.partition.PartitionView;
import com.hazelcast.spi.*;

public abstract class BasePutOperation extends LockAwareOperation implements BackupAwareOperation {

    protected transient Data dataOldValue;
    protected transient EntryEventType eventType;

    public BasePutOperation(String name, Data dataKey, Data value) {
        super(name, dataKey, value, -1);
    }

    public BasePutOperation(String name, Data dataKey, Data value, long ttl) {
        super(name, dataKey, value, ttl);
    }

    public BasePutOperation() {
    }

    public void afterRun() {
        mapService.interceptAfterPut(name, dataValue);
        if (eventType == null)
            eventType = dataOldValue == null ? EntryEventType.ADDED : EntryEventType.UPDATED;
        mapService.publishEvent(getCallerAddress(), name, eventType, dataKey, dataOldValue, dataValue);
        invalidateNearCaches();
        if (mapContainer.getWanReplicationPublisher() != null && mapContainer.getWanMergePolicy() != null) {
            Record record = recordStore.getRecord(dataKey);
            SimpleEntryView entryView = new SimpleEntryView(dataKey, mapService.toData(dataValue), record.getStatistics(), record.getVersion());
            mapService.publishWanReplicationUpdate(name, entryView);
        }
        if (mapContainer.getMapConfig() instanceof ReplicatedMapConfigAdapter) {
            NodeEngine nodeEngine = mapService.getNodeEngine();
            PartitionView partitionView = nodeEngine.getPartitionService().getPartition(getPartitionId());
            for (MemberImpl member : nodeEngine.getClusterService().getMemberList()) {
                Address address = member.getAddress();
                if (!partitionView.isBackup(address)) {
                    OperationService os = nodeEngine.getOperationService();
                    Operation op = new PutReplicateOperation(name, dataKey, dataValue, ttl);
                    op.setPartitionId(getPartitionId()).setServiceName(getServiceName());
                    os.send(op, address);
                }
            }
        }
    }

    public boolean shouldBackup() {
        return true;
    }

    public Operation getBackupOperation() {
        return new PutBackupOperation(name, dataKey, dataValue, ttl);
    }

    public final int getAsyncBackupCount() {
        return mapContainer.getAsyncBackupCount();
    }

    public final int getSyncBackupCount() {
        return mapContainer.getBackupCount();
    }

    public void onWaitExpire() {
        final ResponseHandler responseHandler = getResponseHandler();
        responseHandler.sendResponse(null);
    }

    @Override
    public String toString() {
        return "BasePutOperation{" + name + "}";
    }
}
