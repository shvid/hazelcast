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

package com.hazelcast.collection.multimap.tx;

import com.hazelcast.collection.CollectionContainer;
import com.hazelcast.collection.CollectionDataSerializerHook;
import com.hazelcast.collection.CollectionProxyId;
import com.hazelcast.collection.operations.CollectionKeyBasedOperation;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.BackupOperation;
import com.hazelcast.transaction.TransactionException;

import java.io.IOException;

/**
 * @ali 4/2/13
 */
public class TxnPrepareBackupOperation extends CollectionKeyBasedOperation implements BackupOperation {

    String caller;
    int threadId;
    long ttl;

    public TxnPrepareBackupOperation() {
    }

    public TxnPrepareBackupOperation(CollectionProxyId proxyId, Data dataKey, String caller, int threadId) {
        super(proxyId, dataKey);
        this.caller = caller;
        this.threadId = threadId;
    }

    public void run() throws Exception {
        CollectionContainer container = getOrCreateContainer();
        if (!container.txnLock(dataKey, caller, threadId, ttl + 10000L)){
            throw new TransactionException("Lock is not owned by the transaction !");
        }
    }

    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeUTF(caller);
        out.writeInt(threadId);
        out.writeLong(ttl);
    }

    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        caller = in.readUTF();
        threadId = in.readInt();
        ttl = in.readLong();
    }

    public int getId() {
        return CollectionDataSerializerHook.TXN_PREPARE_BACKUP;
    }
}
