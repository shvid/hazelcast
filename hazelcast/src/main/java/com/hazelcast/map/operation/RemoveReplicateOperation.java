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
import java.util.Collection;

import com.hazelcast.core.EntryEventType;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.map.EventData;
import com.hazelcast.map.MapDataSerializerHook;
import com.hazelcast.map.MapService;
import com.hazelcast.map.RecordStore;
import com.hazelcast.map.record.Record;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.EventRegistration;
import com.hazelcast.spi.EventService;

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
            updateSizeEstimator(-calculateRecordSize(record));
            recordStore.deleteRecord(dataKey);
            mapService.publishReplicatedEvent(name, EntryEventType.REMOVED, dataKey, mapService.toData(record.getValue()), null);
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
