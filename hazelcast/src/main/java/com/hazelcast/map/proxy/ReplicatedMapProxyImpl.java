package com.hazelcast.map.proxy;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hazelcast.config.DistributionStrategyConfig;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.IReplicatedMap;
import com.hazelcast.core.PartitioningStrategy;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.MapService;
import com.hazelcast.map.PartitionContainer;
import com.hazelcast.map.RecordStore;
import com.hazelcast.map.ReplicatedMapConfigAdapter;
import com.hazelcast.map.SimpleEntryView;
import com.hazelcast.map.record.Record;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.partition.PartitionService;
import com.hazelcast.partition.PartitionView;
import com.hazelcast.query.Predicate;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.Operation;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ReplicatedMapProxyImpl<K, V>
        extends MapProxyImpl<K, V> implements IReplicatedMap<K, V> {

	private EntryListenerEventAdapter<K, V> nonIncludedValueAdapter = new EntryListenerEventAdapter<K, V>() {
		
		@Override
		public EntryEvent<K, V> adapt(EntryEvent<K, V> event) {
			return new EntryEvent<K, V>(event.getSource(), event.getMember(),
					event.getEventType().getType(), event.getKey(), null);
		}
	}; 
	
    private final DistributionStrategyConfig distributionStrategyConfig;

    private final PartitionService partitionService;

    private final NodeEngine nodeEngine;

    private final InMemoryFormat inMemoryFormat;

    public ReplicatedMapProxyImpl(String name, MapService service, NodeEngine nodeEngine) {
        super(name, service, nodeEngine);
        this.distributionStrategyConfig = ((ReplicatedMapConfigAdapter) mapConfig).getDistributionStrategyConfig();
        this.partitionService = nodeEngine.getPartitionService();
        this.nodeEngine = nodeEngine;
        this.inMemoryFormat = mapConfig.getInMemoryFormat();
        if (isEventuallyConsistent()) {
            Operation op = new InitiallyFillReplicatedMapOperation(partitionStrategy);
            op.setServiceName(MapService.SERVICE_NAME);
            op.setNodeEngine(nodeEngine);
            op.setService(service);
            nodeEngine.getOperationService().executeOperation(op);
        }
    }

    @Override
	public String addEntryListener(EntryListener listener, boolean includeValue) {
		return addEntryListener(listener, (K) null, includeValue);
	}

	@Override
	public String addEntryListener(EntryListener<K, V> listener, final K key, boolean includeValue) {
		EntryListener<K, V> wrapper = new KeyedEntryListener<K, V>(listener, includeValue ? null : nonIncludedValueAdapter, new EntryListenerPredicate<K, V>() {
			@Override
			public boolean apply(EntryEvent<K, V> event) {
				return key == null || (event.getKey() != null && event.getKey().equals(key));
			}
		});
		return super.addLocalEntryListener(wrapper);
	}

	@Override
	public String addEntryListener(EntryListener<K, V> listener, final Predicate<K, V> predicate, final K key, boolean includeValue) {
		EntryListener<K, V> wrapper = new KeyedEntryListener<K, V>(listener, includeValue ? null : nonIncludedValueAdapter, new EntryListenerPredicate<K, V>() {
			@Override
			public boolean apply(EntryEvent<K, V> event) {
				return key == null || (event.getKey() != null && event.getKey().equals(key) &&
						predicate.apply(new AbstractMap.SimpleImmutableEntry(event.getKey(), event.getValue())));
			}
		});
		return super.addLocalEntryListener(wrapper);
	}

	@Override
	public String addEntryListener(EntryListener<K, V> listener, final Predicate<K, V> predicate, boolean includeValue) {
		EntryListener<K, V> wrapper = new KeyedEntryListener<K, V>(listener, includeValue ? null : nonIncludedValueAdapter, new EntryListenerPredicate<K, V>() {
			@Override
			public boolean apply(EntryEvent<K, V> event) {
				return predicate.apply(new AbstractMap.SimpleImmutableEntry(event.getKey(), event.getValue()));
			}
		});
		return super.addLocalEntryListener(wrapper);
	}

	@Override
    public V get(Object k) {
        Data keyData = getService().toData(k, partitionStrategy);
        int partitionId = partitionService.getPartitionId(keyData);
        return getValueInternal((K) k, keyData, partitionId);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        if (isEventuallyConsistent()) {
            Map<Data, K> ks = new HashMap<Data, K>(keys.size());
            for (K key : keys) {
                if (key == null) {
                    throw new NullPointerException(NULL_KEY_IS_NOT_ALLOWED);
                }
                Data keyData = getService().toData(key, partitionStrategy);
                ks.put(keyData, key);
            }
            Map<K, V> map = new HashMap<K, V>(ks.size());
            for (Entry<Data, K> entry : ks.entrySet()) {
                int partitionId = partitionService.getPartitionId(entry.getKey());
                V v = getValueInternal(entry.getValue(), entry.getKey(), partitionId);
                map.put(entry.getValue(), v);
            }
            return map;
        }
        return super.getAll(keys);
    }

    @Override
    public Future<V> getAsync(final K k) {
        if (isEventuallyConsistent()) {
            return new Future<V>() {
                private volatile boolean cancelled = false;

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    this.cancelled = true;
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }

                @Override
                public boolean isDone() {
                    return !cancelled;
                }

                @Override
                public V get() throws InterruptedException, ExecutionException {
                    Data keyData = getService().toData(k, partitionStrategy);
                    int partitionId = partitionService.getPartitionId(keyData);
                    return getValueInternal(k, keyData, partitionId);
                }

                @Override
                public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    return get();
                }
            };
        }
        return super.getAsync(k);
    }

    @Override
    public EntryView<K, V> getEntryView(K key) {
        if (key == null) {
            throw new NullPointerException(NULL_KEY_IS_NOT_ALLOWED);
        }
        Data kayData = getService().toData(key, partitionStrategy);
        int partitionId = partitionService.getPartitionId(kayData);
        V value = getValueInternal(key, kayData, partitionId);
        if (value == null) {
            return null;
        }
        SimpleEntryView<K, V> entryView = new SimpleEntryView<K, V>();
        entryView.setKey(key);
        entryView.setValue(value);
        return entryView;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (isEventuallyConsistent()) {
            Set<Entry<K, V>> entrySet = new HashSet<Entry<K, V>>();
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    for (Entry<Data, Object> entry : recordStore.entrySetObject()) {
                        K key = (K) getService().toObject(entry.getKey());
                        entrySet.add(new AbstractMap.SimpleImmutableEntry<K, V>(key, (V) entry.getValue()));
                    }
                }
            }
            return entrySet;
        }
        return super.entrySet();
    }

    @Override
    public Set<Entry<K, V>> entrySet(Predicate predicate) {
        if (predicate == null) {
            throw new NullPointerException("Predicate should not be null!");
        }
        if (isEventuallyConsistent()) {
            Set<Entry<K, V>> entrySet = new HashSet<Entry<K, V>>();
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    for (Entry<Data, Object> entry : recordStore.entrySetObject()) {
                        K key = (K) getService().toObject(entry.getKey());
                        Entry<K, V> e = new AbstractMap.SimpleImmutableEntry<K, V>(key, (V) entry.getValue());
                        //TODO Is this enough or does it need an local only implementation of query?
                        if (predicate.apply(e)) {
                            entrySet.add(e);
                        }
                    }
                }
            }
            return entrySet;
        }
        return super.entrySet(predicate);
    }

    @Override
    public boolean containsKey(Object k) {
        if (k == null) {
            throw new NullPointerException(NULL_KEY_IS_NOT_ALLOWED);
        }
        if (isEventuallyConsistent()) {
            Data keyData = getService().toData(k, partitionStrategy);
            int partitionId = partitionService.getPartitionId(keyData);
            RecordStore recordStore = getService().getRecordStore(partitionId, name);
            return recordStore.containsKey(keyData);
        }
        return super.containsKey(k);
    }

    @Override
    public boolean containsValue(Object v) {
        if (v == null) {
            throw new NullPointerException(NULL_VALUE_IS_NOT_ALLOWED);
        }
        if (isEventuallyConsistent()) {
            Data valueData = getService().toData(v);
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    if(recordStore.containsValue(valueData)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return super.containsValue(v);
    }

    @Override
    public Object executeOnKey(K key, EntryProcessor entryProcessor) {
        return super.executeOnKey(key, entryProcessor);    //TODO change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public Set<K> keySet() {
        if (isEventuallyConsistent()) {
            Set<K> keySet = new HashSet<K>();
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    for (Data dataKey : recordStore.keySet()) {
                        K key = (K) getService().toObject(dataKey);
                        keySet.add(key);
                    }
                }
            }
            return keySet;
        }
        return super.keySet();
    }

    @Override
    public Set<K> keySet(Predicate predicate) {
        if (predicate == null) {
            throw new NullPointerException("Predicate should not be null!");
        }
        if (isEventuallyConsistent()) {
            Set<K> keySet = new HashSet<K>();
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    for (Entry<Data, Object> entry : recordStore.entrySetObject()) {
                        K key = (K) getService().toObject(entry.getKey());
                        Entry<K, V> e = new AbstractMap.SimpleImmutableEntry<K, V>(key, (V) entry.getValue());
                        //TODO Is this enough or does it need an local only implementation of query?
                        if (predicate.apply(e)) {
                            keySet.add(key);
                        }
                    }
                }
            }
            return keySet;
        }
        return super.keySet(predicate);
    }

    @Override
    public Set<K> localKeySet() {
        if (isEventuallyConsistent()) {
            return keySet();
        }
        return super.localKeySet();
    }

    @Override
    public Set<K> localKeySet(Predicate predicate) {
        if (isEventuallyConsistent()) {
            return keySet(predicate);
        }
        return super.localKeySet(predicate);
    }

    @Override
    public Collection<V> values() {
        if (isEventuallyConsistent()) {
            List<V> values = new ArrayList<V>();
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    for (Object value : recordStore.valuesObject()) {
                        values.add((V) value);
                    }
                }
            }
            return values;
        }
        return super.values();
    }

    @Override
    public Collection<V> values(Predicate predicate) {
        if (predicate == null) {
            throw new NullPointerException("Predicate should not be null!");
        }
        if (isEventuallyConsistent()) {
            List<V> values = new ArrayList<V>();
            int partitionCount = partitionService.getPartitionCount();
            for (int i = 0; i < partitionCount; i++) {
                PartitionContainer partitionContainer = getService().getPartitionContainer(i);
                if (partitionContainer != null) {
                    RecordStore recordStore = partitionContainer.getRecordStore(name);
                    for (Entry<Data, Object> entry : recordStore.entrySetObject()) {
                        K key = (K) getService().toObject(entry.getKey());
                        Entry<K, V> e = new AbstractMap.SimpleImmutableEntry<K, V>(key, (V) entry.getValue());
                        //TODO Is this enough or does it need an local only implementation of query?
                        if (predicate.apply(e)) {
                            values.add((V) entry.getValue());
                        }
                    }
                }
            }
            return values;
        }
        return super.values(predicate);
    }

    @Override
    public boolean isEventuallyConsistent() {
        return distributionStrategyConfig == DistributionStrategyConfig.Distributed;
    }

    protected V findValueInBackup(Data keyData, int partitionId) {
        PartitionView partitionView = partitionService.getPartition(partitionId);
        Address address = nodeEngine.getThisAddress();
        if (partitionView.getReplicaIndexOf(address) != -1) {
            RecordStore recordStore = getService().getRecordStore(partitionId, name);
            V value = (V) recordStore.get(keyData);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected V findInLocalPartitions(Data keyData, int partitionId) {
        if (nodeEngine.getThisAddress().equals(partitionService.getPartitionOwner(partitionId))) {
            RecordStore recordStore = getService().getRecordStore(partitionId, name);
            Record<V> record = recordStore.getRecord(keyData);
            if (record != null) {
                return record.getValue();
            }
        }
        return null;
    }

    protected void storeToLocalRecordStore(Data keyData, Object value, int partitionId) {
        RecordStore recordStore = getService().getRecordStore(partitionId, name);
        int ttl = mapConfig.getTimeToLiveSeconds();
        recordStore.put(keyData, convertValue(value), getTimeInMillis(ttl, TimeUnit.SECONDS));
    }

    protected V getValueInternal(K key, Data keyData, int partitionId) {
        Object value = findInLocalPartitions(keyData, partitionId);
        if (value != null) {
            return (V) getService().toObject(value);
        }
        value = findValueInBackup(keyData, partitionId);
        if (value != null) {
            return (V) getService().toObject(value);
        }
        if (!isEventuallyConsistent()) {
            value = super.get(key);
            if (value != null) {
                storeToLocalRecordStore(keyData, value, partitionId);
            }
        }
        return (V) getService().toObject(value);
    }

    private Set<Entry<K, V>> getFullEntrySetInternal() {
        return super.entrySet();
    }

    private Object convertValue(Object value) {
        if (inMemoryFormat == InMemoryFormat.OBJECT) {
            if (value instanceof Data) {
                return getService().toObject(value);
            }
            return value;
        }
        return getService().toData(value);
    }

    @SuppressWarnings("serial")
	private class InitiallyFillReplicatedMapOperation extends Operation {
        private final PartitioningStrategy<K> partitioningStrategy;

        public InitiallyFillReplicatedMapOperation(PartitioningStrategy<K> partitioningStrategy) {
            this.partitioningStrategy = partitioningStrategy;
        }

        @Override
        public void beforeRun() throws Exception {
        }

        @Override
        public void run() throws Exception {
            Set<Entry<K, V>> entrySet = getFullEntrySetInternal();
            MapService mapService = getService();
            for (Entry<K, V> entry : entrySet) {
                Data keyData = mapService.toData(entry.getKey(), partitioningStrategy);
                Object value = convertValue(entry.getValue());
                int partitionId = partitionService.getPartitionId(keyData);
                storeToLocalRecordStore(keyData, value, partitionId);
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
            throw new UnsupportedOperationException("InitiallyFillReplicatedMapOperation cannot be serialized");
        }

        @Override
        protected void readInternal(ObjectDataInput in) throws IOException {
            throw new UnsupportedOperationException("InitiallyFillReplicatedMapOperation cannot be serialized");
        }
    }
    
    private static class KeyedEntryListener<K, V> implements EntryListener<K, V> {

    	private final EntryListenerEventAdapter<K, V> adapter;
    	private final EntryListenerPredicate<K, V> predicate;
    	private final EntryListener<K, V> entryListener;
    	
    	private KeyedEntryListener(EntryListener<K, V> entryListener, EntryListenerEventAdapter<K, V> adapter, EntryListenerPredicate<K, V> predicate) {
    		if(entryListener == null) {
    			throw new NullPointerException("entryListener must not be null");
    		}
    		if(predicate == null) {
    			throw new NullPointerException("predicate must not be null");
    		}
    		this.entryListener = entryListener;
    		this.predicate = predicate;
    		this.adapter = adapter;
    	}
    	
		@Override
		public void entryAdded(EntryEvent<K, V> event) {
			if (predicate.apply(event)) {
				entryListener.entryAdded(adapt(event));
			}
		}

		@Override
		public void entryRemoved(EntryEvent<K, V> event) {
			if (predicate.apply(event)) {
				entryListener.entryRemoved(adapt(event));
			}
		}

		@Override
		public void entryUpdated(EntryEvent<K, V> event) {
			if (predicate.apply(event)) {
				entryListener.entryUpdated(adapt(event));
			}
		}

		@Override
		public void entryEvicted(EntryEvent<K, V> event) {
			if (predicate.apply(event)) {
				entryListener.entryEvicted(adapt(event));
			}
		}
		
		private EntryEvent<K, V> adapt(EntryEvent<K, V> event) {
			if (adapter == null) {
				return event;
			}
			return adapter.adapt(event);
		}
    }
    
    private static interface EntryListenerPredicate<K, V> {
    	boolean apply(EntryEvent<K, V> event);
    }
    
    private static interface EntryListenerEventAdapter<K, V> {
    	EntryEvent<K, V> adapt(EntryEvent<K, V> event);
    }
    
}
