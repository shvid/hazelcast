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

import java.io.IOException;

import com.hazelcast.core.EntryEventType;
import com.hazelcast.map.MapDataSerializerHook;
import com.hazelcast.map.record.Record;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.partition.PartitionService;
import com.hazelcast.partition.PartitionView;

public final class PutReplicateOperation extends BaseReplicateOperation implements IdentifiedDataSerializable {

    public PutReplicateOperation(String name, Data dataKey, Data dataValue, long ttl) {
        super(name, dataKey, dataValue, ttl);
    }

    public PutReplicateOperation() {
    }

    public void run() {
    	PartitionService partitionService = getNodeEngine().getPartitionService();
    	PartitionView partitionView = partitionService.getPartition(partitionContainer.getPartitionId());
    	if (partitionView.getReplicaIndexOf(getNodeEngine().getThisAddress()) != -1) {
    		return;
    	}

        Record record = recordStore.getRecord(dataKey);
        if (record == null) {
            record = mapService.createRecord(name, dataKey, dataValue, ttl, false);
            updateSizeEstimator(calculateRecordSize(record));
            recordStore.putRecord(dataKey, record);
            mapService.publishReplicatedEvent(name, EntryEventType.ADDED, dataKey, null, dataValue);
        } else {
        	Object oldValue = record.getValue();
            EntryEventType eventType = oldValue == null ? EntryEventType.ADDED : EntryEventType.UPDATED;
            updateSizeEstimator(-calculateRecordSize(record));
            mapContainer.getRecordFactory().setValue(record, dataValue);
            updateSizeEstimator(calculateRecordSize(record));
            mapService.publishReplicatedEvent(name, eventType, dataKey, mapService.toData(oldValue), dataValue);
        }
    }

    @Override
	public boolean returnsResponse() {
		return false;
	}

	@Override
    public Object getResponse() {
        return null;
    }

    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
    }

    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
    }

    @Override
    public String toString() {
        return "PutReplicateOperation{" + name + "}";
    }

    public int getFactoryId() {
        return MapDataSerializerHook.F_ID;
    }

    public int getId() {
        return MapDataSerializerHook.PUT_REPLICATED_MAP;
    }

    private void updateSizeEstimator( long recordSize ) {
        recordStore.getSizeEstimator().add( recordSize );
    }

    private long calculateRecordSize( Record record ) {
        return recordStore.getSizeEstimator().getCost(record);
    }

}