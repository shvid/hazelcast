package com.hazelcast.map;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.IReplicatedMap;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.query.Predicate;

public class ReplicatedMapWrapper<K, V> implements IReplicatedMap<K, V> {

	private final IReplicatedMap<K, V> wrapped;

	private final Map<K, CountDownLatch> puts = new ConcurrentHashMap<K, CountDownLatch>();

	ReplicatedMapWrapper(IReplicatedMap<K, V> wrapped) {
		this.wrapped = wrapped;
		this.wrapped.addLocalEntryListener(new EntryListener<K, V>() {

			@Override
			public void entryAdded(EntryEvent<K, V> event) {
				CountDownLatch latch = puts.get(event.getKey());
				if (latch != null) {
					latch.countDown();
				} else {
					System.out.println(event);
				}
			}

			@Override
			public void entryRemoved(EntryEvent<K, V> event) {
				CountDownLatch latch = puts.get(event.getKey());
				if (latch != null) {
					latch.countDown();
				} else {
					System.out.println(event);
				}
			}

			@Override
			public void entryUpdated(EntryEvent<K, V> event) {
				CountDownLatch latch = puts.get(event.getKey());
				if (latch != null) {
					latch.countDown();
				} else {
					System.out.println(event);
				}
			}

			@Override
			public void entryEvicted(EntryEvent<K, V> event) {
				CountDownLatch latch = puts.get(event.getKey());
				if (latch != null) {
					latch.countDown();
				} else {
					System.out.println(event);
				}
			}
		});
	}

	public boolean isEventuallyConsistent() {
		return wrapped.isEventuallyConsistent();
	}

	public Object getId() {
		return wrapped.getId();
	}

	public String getPartitionKey() {
		return wrapped.getPartitionKey();
	}

	public String getName() {
		return wrapped.getName();
	}

	public String getServiceName() {
		return wrapped.getServiceName();
	}

	public void destroy() {
		wrapped.destroy();
	}

	public boolean containsKey(Object key) {
		return wrapped.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return wrapped.containsValue(value);
	}

	public V get(Object key) {
		return wrapped.get(key);
	}

	public V put(K key, V value) {
		CountDownLatch latch = new CountDownLatch(1);
		puts.put(key, latch);
		V v = wrapped.put(key, value);
		try {
			latch.await();
			puts.remove(key);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return v;
	}

	public V remove(Object key) {
		return wrapped.remove(key);
	}

	public boolean remove(Object key, Object value) {
		return wrapped.remove(key, value);
	}

	public int size() {
		return wrapped.size();
	}

	public void delete(Object key) {
		wrapped.delete(key);
	}

	public boolean isEmpty() {
		return wrapped.isEmpty();
	}

	public void flush() {
		wrapped.flush();
	}

	public Map<K, V> getAll(Set<K> keys) {
		return wrapped.getAll(keys);
	}

	public Future<V> getAsync(K key) {
		return wrapped.getAsync(key);
	}

	public Future<V> putAsync(K key, V value) {
		return wrapped.putAsync(key, value);
	}

	public Future<V> putAsync(K key, V value, long ttl, TimeUnit timeunit) {
		return wrapped.putAsync(key, value, ttl, timeunit);
	}

	public Future<V> removeAsync(K key) {
		return wrapped.removeAsync(key);
	}

	public boolean tryRemove(K key, long timeout, TimeUnit timeunit) {
		return wrapped.tryRemove(key, timeout, timeunit);
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		wrapped.putAll(m);
	}

	public boolean tryPut(K key, V value, long timeout, TimeUnit timeunit) {
		return wrapped.tryPut(key, value, timeout, timeunit);
	}

	public void clear() {
		wrapped.clear();
	}

	public V put(K key, V value, long ttl, TimeUnit timeunit) {
		CountDownLatch latch = new CountDownLatch(1);
		puts.put(key, latch);
		V v = wrapped.put(key, value, ttl, timeunit);
		try {
			latch.await();
			puts.remove(key);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return v;
	}

	public void putTransient(K key, V value, long ttl, TimeUnit timeunit) {
		wrapped.putTransient(key, value, ttl, timeunit);
	}

	public V putIfAbsent(K key, V value) {
		return wrapped.putIfAbsent(key, value);
	}

	public V putIfAbsent(K key, V value, long ttl, TimeUnit timeunit) {
		return wrapped.putIfAbsent(key, value, ttl, timeunit);
	}

	public boolean replace(K key, V oldValue, V newValue) {
		return wrapped.replace(key, oldValue, newValue);
	}

	public V replace(K key, V value) {
		return wrapped.replace(key, value);
	}

	public void set(K key, V value) {
		wrapped.set(key, value);
	}

	public void set(K key, V value, long ttl, TimeUnit timeunit) {
		wrapped.set(key, value, ttl, timeunit);
	}

	public void lock(K key) {
		wrapped.lock(key);
	}

	public void lock(K key, long leaseTime, TimeUnit timeUnit) {
		wrapped.lock(key, leaseTime, timeUnit);
	}

	public boolean equals(Object o) {
		return wrapped.equals(o);
	}

	public boolean isLocked(K key) {
		return wrapped.isLocked(key);
	}

	public int hashCode() {
		return wrapped.hashCode();
	}

	public boolean tryLock(K key) {
		return wrapped.tryLock(key);
	}

	public boolean tryLock(K key, long time, TimeUnit timeunit) throws InterruptedException {
		return wrapped.tryLock(key, time, timeunit);
	}

	public void unlock(K key) {
		wrapped.unlock(key);
	}

	public void forceUnlock(K key) {
		wrapped.forceUnlock(key);
	}

	public String addLocalEntryListener(EntryListener<K, V> listener) {
		return wrapped.addLocalEntryListener(listener);
	}

	public String addInterceptor(MapInterceptor interceptor) {
		return wrapped.addInterceptor(interceptor);
	}

	public void removeInterceptor(String id) {
		wrapped.removeInterceptor(id);
	}

	public String addEntryListener(EntryListener<K, V> listener, boolean includeValue) {
		return wrapped.addEntryListener(listener, includeValue);
	}

	public boolean removeEntryListener(String id) {
		return wrapped.removeEntryListener(id);
	}

	public String addEntryListener(EntryListener<K, V> listener, K key, boolean includeValue) {
		return wrapped.addEntryListener(listener, key, includeValue);
	}

	public String addEntryListener(EntryListener<K, V> listener, Predicate<K, V> predicate, boolean includeValue) {
		return wrapped.addEntryListener(listener, predicate, includeValue);
	}

	public String addEntryListener(EntryListener<K, V> listener, Predicate<K, V> predicate, K key, boolean includeValue) {
		return wrapped.addEntryListener(listener, predicate, key, includeValue);
	}

	public EntryView<K, V> getEntryView(K key) {
		return wrapped.getEntryView(key);
	}

	public boolean evict(K key) {
		return wrapped.evict(key);
	}

	public Set<K> keySet() {
		return wrapped.keySet();
	}

	public Collection<V> values() {
		return wrapped.values();
	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return wrapped.entrySet();
	}

	public Set<K> keySet(Predicate predicate) {
		return wrapped.keySet(predicate);
	}

	public Set<java.util.Map.Entry<K, V>> entrySet(Predicate predicate) {
		return wrapped.entrySet(predicate);
	}

	public Collection<V> values(Predicate predicate) {
		return wrapped.values(predicate);
	}

	public Set<K> localKeySet() {
		return wrapped.localKeySet();
	}

	public Set<K> localKeySet(Predicate predicate) {
		return wrapped.localKeySet(predicate);
	}

	public void addIndex(String attribute, boolean ordered) {
		wrapped.addIndex(attribute, ordered);
	}

	public LocalMapStats getLocalMapStats() {
		return wrapped.getLocalMapStats();
	}

	public Object executeOnKey(K key, EntryProcessor entryProcessor) {
		return wrapped.executeOnKey(key, entryProcessor);
	}

	public Map<K, Object> executeOnEntries(EntryProcessor entryProcessor) {
		return wrapped.executeOnEntries(entryProcessor);
	}

}
