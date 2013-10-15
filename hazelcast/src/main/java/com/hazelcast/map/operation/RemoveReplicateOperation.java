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
import com.hazelcast.map.MapService;
import com.hazelcast.map.RecordStore;
import com.hazelcast.map.record.Record;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public final class RemoveReplicateOperation extends BaseReplicateOperation implements IdentifiedDataSerializable {

    public RemoveReplicateOperation(String name, Data dataKey) {
        super(name, dataKey);
    }

    public RemoveReplicateOperation() {
    }

    public void run() {
        MapService mapService = getService();
        int partitionId = getPartitionId();
        RecordStore recordStore = mapService.getRecordStore(partitionId, name);
        Record record = recordStore.getRecord(dataKey);
        if (record != null) {
        	Object oldValue = record.getValue();
            updateSizeEstimator(-calculateRecordSize(record));
            recordStore.deleteRecord(dataKey);
            Data dataOldValue = mapService.toData(oldValue);
            mapService.publishReplicatedEvent(name, EntryEventType.REMOVED, dataKey, dataOldValue, dataOldValue);
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

    public int getFactoryId() {
        return MapDataSerializerHook.F_ID;
    }

    public int getId() {
        return MapDataSerializerHook.REMOVE_REPLICATED_MAP;
    }

    private void updateSizeEstimator(long recordSize) {
        recordStore.getSizeEstimator().add(recordSize);
    }

    private long calculateRecordSize(Record record) {
        return recordStore.getSizeEstimator().getCost(record);
    }
}
