package com.hazelcast.client.txn;

import com.hazelcast.client.CallableClientRequest;
import com.hazelcast.client.ClientEndpoint;
import com.hazelcast.client.ClientEngineImpl;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;

import java.io.IOException;

/**
 * @ali 6/7/13
 */
public class CommitTransactionRequest extends CallableClientRequest implements Portable {

    public CommitTransactionRequest() {
    }

    public Object call() throws Exception {
        final ClientEndpoint endpoint = getEndpoint();
        endpoint.getTransactionContext().commitTransaction();
        return null;
    }

    public String getServiceName() {
        return ClientEngineImpl.SERVICE_NAME;
    }

    public int getFactoryId() {
        return ClientTxnPortableHook.F_ID;
    }

    public int getClassId() {
        return ClientTxnPortableHook.COMMIT;
    }

    public void writePortable(PortableWriter writer) throws IOException {

    }

    public void readPortable(PortableReader reader) throws IOException {

    }
}
