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

package com.hazelcast.concurrent.semaphore;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.PartitionAwareOperation;
import com.hazelcast.spi.impl.AbstractNamedOperation;

import java.io.IOException;

/**
 * @ali 1/22/13
 */
public abstract class SemaphoreOperation extends AbstractNamedOperation implements PartitionAwareOperation {

    int permitCount;

    transient Object response;

    protected SemaphoreOperation() {
    }

    protected SemaphoreOperation(String name, int permitCount) {
        super(name);
        this.permitCount = permitCount;
    }

    public Object getResponse() {
        return response;
    }

    public Permit getPermit(){
        SemaphoreService service = getService();
        return service.getOrCreatePermit(name);
    }

    public void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeInt(permitCount);
    }

    public void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        permitCount = in.readInt();
    }
}
