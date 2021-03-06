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

package com.hazelcast.collection.operations;

import com.hazelcast.collection.CollectionProxyId;
import com.hazelcast.collection.CollectionRecord;
import com.hazelcast.collection.CollectionWrapper;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.PartitionAwareOperation;

import java.io.IOException;
import java.util.Collection;

/**
 * @ali 1/16/13
 */
public abstract class CollectionKeyBasedOperation extends CollectionOperation implements PartitionAwareOperation {

    protected Data dataKey;

    protected CollectionKeyBasedOperation() {
    }

    protected CollectionKeyBasedOperation(CollectionProxyId proxyId, Data dataKey) {
        super(proxyId);
        this.dataKey = dataKey;
    }

    public final CollectionWrapper getOrCreateCollectionWrapper() {
        return getOrCreateContainer().getOrCreateCollectionWrapper(dataKey);
    }

    public final CollectionWrapper getCollectionWrapper() {
        return getOrCreateContainer().getCollectionWrapper(dataKey);
    }

    public final Collection<CollectionRecord> removeCollection() {
        return getOrCreateContainer().removeCollection(dataKey);
    }

    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        dataKey.writeData(out);
    }

    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        dataKey = new Data();
        dataKey.readData(in);
    }


}
