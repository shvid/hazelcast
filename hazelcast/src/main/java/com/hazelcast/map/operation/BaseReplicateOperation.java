package com.hazelcast.map.operation;

import java.io.IOException;

import com.hazelcast.map.MapContainer;
import com.hazelcast.map.MapService;
import com.hazelcast.map.PartitionContainer;
import com.hazelcast.map.RecordStore;
import com.hazelcast.nio.IOUtil;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.Operation;

public abstract class BaseReplicateOperation extends Operation {

    protected String name;
    protected Data dataKey;
    protected Data dataValue = null;
    protected long ttl = -1;

    protected transient MapService mapService;
    protected transient MapContainer mapContainer;
    protected transient PartitionContainer partitionContainer;
    protected transient RecordStore recordStore;

	public BaseReplicateOperation() {
	}
	
	public BaseReplicateOperation(String name, Data dataKey) {
        this.dataKey = dataKey;
        this.name = name;
	}
	
	protected BaseReplicateOperation(String name, Data dataKey, Data dataValue) {
        this.name = name;
        this.dataKey = dataKey;
        this.dataValue = dataValue;
	}
	
    protected BaseReplicateOperation(String name, Data dataKey, long ttl) {
        this.name = name;
        this.dataKey = dataKey;
        this.ttl = ttl;
    }

    protected BaseReplicateOperation(String name, Data dataKey, Data dataValue, long ttl) {
        this.name = name;
        this.dataKey = dataKey;
        this.dataValue = dataValue;
        this.ttl = ttl;
    }

    public final String getName() {
        return name;
    }

    public final Data getKey() {
        return dataKey;
    }

    public final Data getValue() {
        return dataValue;
    }

    public final long getTtl() {
        return ttl;
    }
	
	@Override
	public void beforeRun() throws Exception {
        mapService = getService();
        mapContainer = mapService.getMapContainer(name);
        partitionContainer = mapService.getPartitionContainer(getPartitionId());
        recordStore = partitionContainer.getRecordStore(name);
        innerBeforeRun();
    }

    public void innerBeforeRun() {
    }
    
    protected final void invalidateNearCaches() {
        if (mapContainer.isNearCacheEnabled()
                && mapContainer.getMapConfig().getNearCacheConfig().isInvalidateOnChange()) {
            mapService.invalidateAllNearCaches(name, dataKey);
        }
    }

	@Override
	public void afterRun() throws Exception {
	}

	@Override
	public boolean returnsResponse() {
		return false;
	}

	@Override
	public Object getResponse() {
		return null;
	}

	@Override
	protected void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeUTF(name);
        dataKey.writeData(out);
        IOUtil.writeNullableData(out, dataValue);
        out.writeLong(ttl);
	}

	@Override
	protected void readInternal(ObjectDataInput in) throws IOException {
        name = in.readUTF();
        dataKey = new Data();
        dataKey.readData(in);
        dataValue = IOUtil.readNullableData(in);
        ttl = in.readLong();
	}

}
