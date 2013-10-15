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

package com.hazelcast.map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import com.hazelcast.config.Config;
import com.hazelcast.core.EntryAdapter;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IReplicatedMap;
import com.hazelcast.core.MapLoader;
import com.hazelcast.core.MapStoreFactory;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.query.Predicate;
import com.hazelcast.test.HazelcastJUnit4ClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.util.Clock;

@RunWith(HazelcastJUnit4ClassRunner.class)
@Category(ParallelTest.class)
public class BasicTest extends HazelcastTestSupport {

    private static final int instanceCount = 3;
    private static final Random rand = new Random(Clock.currentTimeMillis());

    private HazelcastInstance[] instances;

    @Before
    public void init() {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(instanceCount);
        Config config = new Config();
        instances = factory.newInstances(config);
    }

    private HazelcastInstance getInstance0() {
        return instances[rand.nextInt(instanceCount)];
    }
    
    private <K, V> IReplicatedMap<K, V> getReplicatedMap(String name) {
    	HazelcastInstance hz = getInstance0();
    	return new ReplicatedMapWrapper<K, V>(hz.<K, V> getReplicatedMap(name));
    }

    @Test
    public void testMapPutAndGet() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapPutAndGet");
        String value = map.put("Hello", "World");
        assertEquals("World", map.get("Hello"));
        assertEquals(1, map.size());
        assertNull(value);
        value = map.put("Hello", "World");
        assertEquals("World", map.get("Hello"));
        assertEquals(1, map.size());
        assertEquals("World", value);
        value = map.put("Hello", "New World");
        assertEquals("World", value);
        assertEquals("New World", map.get("Hello"));
    }

    @Test
    public void testMapPutIfAbsent() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapPutIfAbsent");
        assertEquals(map.putIfAbsent("key1", "value1"), null);
        assertEquals(map.putIfAbsent("key2", "value2"), null);
        assertEquals(map.putIfAbsent("key1", "valueX"), "value1");
        assertEquals(map.get("key1"), "value1");
        assertEquals(map.size(), 2);
    }

    @Test
    public void testMapGetNullIsNotAllowed() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapGetNullIsNotAllowed");
        try {
            map.get(null);
            fail();
        }
        catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void valuesToArray() {
        IReplicatedMap<String, String> map = getReplicatedMap("valuesToArray");
        assertEquals(0, map.size());
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        assertEquals(3, map.size());
        {
            final Object[] values = map.values().toArray();
            Arrays.sort(values);
            assertArrayEquals(new Object[]{"1", "2", "3"}, values);
        }
        {
            final String[] values = map.values().toArray(new String[3]);
            Arrays.sort(values);
            assertArrayEquals(new String[]{"1", "2", "3"}, values);
        }
        {
            final String[] values = map.values().toArray(new String[2]);
            Arrays.sort(values);
            assertArrayEquals(new String[]{"1", "2", "3"}, values);
        }
        {
            final String[] values = map.values().toArray(new String[5]);
            Arrays.sort(values, 0, 3);
            assertArrayEquals(new String[]{"1", "2", "3", null, null}, values);
        }
    }

    @Test
    @Ignore(value="TTL / eviction not yet supported on ReplicatedMap")
    public void testMapEvictAndListener() throws InterruptedException {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapEvictAndListener");
        final String value1 = "/home/data/file1.dat";
        final String value2 = "/home/data/file2.dat";

        final List<String> newList = new CopyOnWriteArrayList<String>();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        map.addEntryListener(new EntryAdapter<String, String>() {
            public void entryEvicted(EntryEvent<String, String> event) {
                if (value1.equals(event.getValue())) {
                    newList.add(event.getValue());
                    latch1.countDown();
                } else if (value2.equals(event.getValue())) {
                    newList.add(event.getValue());
                    latch2.countDown();
                }
            }
        }, true);

        map.put("key", value1, 1, TimeUnit.SECONDS);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));

        map.put("key", value2, 1, TimeUnit.SECONDS);
        assertTrue(latch2.await(10, TimeUnit.SECONDS));

        assertEquals(value1, newList.get(0));
        assertEquals(value2, newList.get(1));
    }

    @Test
    @Ignore(value="TTL / eviction not yet supported on ReplicatedMap")
    public void testMapEntryListener() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapEntryListener");
        final CountDownLatch latchAdded = new CountDownLatch(1);
        final CountDownLatch latchRemoved = new CountDownLatch(1);
        final CountDownLatch latchUpdated = new CountDownLatch(1);
        map.addEntryListener(new EntryListener<String, String>() {
            public void entryAdded(EntryEvent event) {
                assertEquals("world", event.getValue());
                assertEquals("hello", event.getKey());
                latchAdded.countDown();
            }

            public void entryRemoved(EntryEvent event) {
                assertEquals("hello", event.getKey());
                assertEquals("new world", event.getValue());
                latchRemoved.countDown();
            }

            public void entryUpdated(EntryEvent event) {
                assertEquals("world", event.getOldValue());
                assertEquals("new world", event.getValue());
                assertEquals("hello", event.getKey());
                latchUpdated.countDown();
            }

            public void entryEvicted(EntryEvent event) {
                entryRemoved(event);
            }
        }, true);
        map.put("hello", "world");
        map.put("hello", "new world");
        map.remove("hello");
        try {
            assertTrue(latchAdded.await(5, TimeUnit.SECONDS));
            assertTrue(latchUpdated.await(5, TimeUnit.SECONDS));
            assertTrue(latchRemoved.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
            assertFalse(e.getMessage(), true);
        }
    }

    /**
     * Test for issue #181
     */
    @Test
    public void testMapKeyListenerWithRemoveAndUnlock() throws InterruptedException {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapKeyListenerWithRemoveAndUnlock");
        final String key = "key";
        final int count = 20;
        final CountDownLatch latch = new CountDownLatch(count * 2);
        map.addEntryListener(new EntryAdapter<String, String>() {
            public void entryAdded(final EntryEvent<String, String> e) {
                testEvent(e);
            }

            public void entryRemoved(final EntryEvent<String, String> e) {
                testEvent(e);
            }

            private void testEvent(final EntryEvent<String, String> e) {
                if (key.equals(e.getKey())) {
                    latch.countDown();
                } else {
                    fail("Invalid event: " + e);
                }
            }
        }, key, true);

        for (int i = 0; i < count; i++) {
            map.lock(key);
            map.put(key, "value");
            map.remove(key);
            map.unlock(key);
        }
        assertTrue("Listener events are missing! Remaining: " + latch.getCount(),
                latch.await(5, TimeUnit.SECONDS));
    }


    @Test
    public void testMapEntrySetWhenRemoved() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapEntrySetWhenRemoved");
        map.put("Hello", "World");
        map.remove("Hello");
        Set<IReplicatedMap.Entry<String, String>> set = map.entrySet();
        for (IReplicatedMap.Entry<String, String> e : set) {
            fail("Iterator should not contain removed entry, found " + e.getKey());
        }
    }


    @Test
    public void testMapRemove() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapRemove");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        assertEquals(map.remove("key1"), "value1");
        assertEquals(map.size(), 2);
        assertEquals(map.remove("key1"), null);
        assertEquals(map.size(), 2);
        assertEquals(map.remove("key3"), "value3");
        assertEquals(map.size(), 1);
    }

    @Test
    public void testMapDelete() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapRemove");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        map.delete("key1");
        assertEquals(map.size(), 2);
        map.delete("key1");
        assertEquals(map.size(), 2);
        map.delete("key3");
        assertEquals(map.size(), 1);
    }

    @Test
    public void testMapClear() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapClear");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        map.clear();
        assertEquals(map.size(), 0);
        assertEquals(map.get("key1"), null);
        assertEquals(map.get("key2"), null);
        assertEquals(map.get("key3"), null);
    }

    @Test
    public void testMapEvict() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapEvict");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        assertEquals(map.remove("key1"), "value1");
        assertEquals(map.size(), 2);
        assertEquals(map.remove("key1"), null);
        assertEquals(map.size(), 2);
        assertEquals(map.remove("key3"), "value3");
        assertEquals(map.size(), 1);
    }

    @Test
    public void testMapTryRemove() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapTryRemove");
        map.put("key1", "value1");
        map.lock("key1");
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final CountDownLatch latch3 = new CountDownLatch(1);
        final AtomicBoolean firstBool = new AtomicBoolean();
        final AtomicBoolean secondBool = new AtomicBoolean();
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    firstBool.set(map.tryRemove("key1", 1, TimeUnit.SECONDS));
                    latch2.countDown();
                    latch1.await();
                    secondBool.set(map.tryRemove("key1", 1, TimeUnit.SECONDS));
                    latch3.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        });
        thread.start();
        latch2.await();
        map.unlock("key1");
        latch1.countDown();
        latch3.await();
        assertFalse(firstBool.get());
        assertTrue(secondBool.get());
        thread.join();
    }

    @Test
    public void testMapRemoveIfSame() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapRemoveIfSame");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        assertFalse(map.remove("key1", "nan"));
        assertEquals(map.size(), 3);
        assertTrue(map.remove("key1", "value1"));
        assertEquals(map.size(), 2);
        assertTrue(map.remove("key2", "value2"));
        assertTrue(map.remove("key3", "value3"));
        assertEquals(map.size(), 0);
    }


    @Test
    public void testMapSet() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapSet");
        map.put("key1", "value1");
        assertEquals(map.get("key1"), "value1");
        assertEquals(map.size(), 1);
        map.set("key1", "valueX", 0, TimeUnit.MILLISECONDS);
        assertEquals(map.size(), 1);
        assertEquals(map.get("key1"), "valueX");
        map.set("key2", "value2", 0, TimeUnit.MILLISECONDS);
        assertEquals(map.size(), 2);
        assertEquals(map.get("key1"), "valueX");
        assertEquals(map.get("key2"), "value2");
    }


    @Test
    public void testMapContainsKey() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapContainsKey");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        assertEquals(map.containsKey("key1"), true);
        assertEquals(map.containsKey("key5"), false);
        map.remove("key1");
        assertEquals(map.containsKey("key1"), false);
        assertEquals(map.containsKey("key2"), true);
        assertEquals(map.containsKey("key5"), false);
    }

    @Test
    public void testMapKeySet() throws Exception {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapKeySet");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        
        Thread.sleep(2500);
        
        HashSet<String> actual = new HashSet<String>();
        actual.add("key1");
        actual.add("key2");
        actual.add("key3");
        assertEquals(actual, map.keySet());
    }

    @Test
    public void testMapLocalKeySet() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapKeySet");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        HashSet<String> actual = new HashSet<String>();
        actual.add("key1");
        actual.add("key2");
        actual.add("key3");
        assertEquals(map.keySet(), actual);
    }

    @Test
    public void testMapValues() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapValues");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        map.put("key4", "value3");
        List<String> values = new ArrayList<String>(map.values());
        List<String> actual = new ArrayList<String>();
        actual.add("value1");
        actual.add("value2");
        actual.add("value3");
        actual.add("value3");
        Collections.sort(values);
        Collections.sort(actual);
        assertEquals(values, actual);
    }

    @Test
    public void testMapContainsValue() {
        IReplicatedMap map = getReplicatedMap("testMapContainsValue");
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        assertTrue(map.containsValue(1));
        assertFalse(map.containsValue(5));
        map.remove(1);
        assertFalse(map.containsValue(1));
        assertTrue(map.containsValue(2));
        assertFalse(map.containsValue(5));
    }

    @Test
    public void testMapIsEmpty() {
        IReplicatedMap<String, String> map = getReplicatedMap("testMapIsEmpty");
        assertTrue(map.isEmpty());
        map.put("key1", "value1");
        assertFalse(map.isEmpty());
        map.remove("key1");
        assertTrue(map.isEmpty());
    }

    @Test
    public void testMapSize() {
        IReplicatedMap map = getReplicatedMap("testMapSize");
        assertEquals(map.size(), 0);
        map.put(1, 1);
        assertEquals(map.size(), 1);
        map.put(2, 2);
        map.put(3, 3);
        assertEquals(map.size(), 3);
    }

    @Test
    public void testMapReplace() {
        IReplicatedMap map = getReplicatedMap("testMapReplace");
        map.put(1, 1);
        assertNull(map.replace(2, 1));
        assertNull(map.get(2));
        map.put(2, 2);
        assertEquals(2, map.replace(2, 3));
        assertEquals(3, map.get(2));
    }

    @Test
    public void testMapReplaceIfSame() {
        IReplicatedMap map = getReplicatedMap("testMapReplaceIfSame");
        map.put(1, 1);
        assertFalse(map.replace(1, 2, 3));
        assertTrue(map.replace(1, 1, 2));
        assertEquals(map.get(1), 2);
        map.put(2, 2);
        assertTrue(map.replace(2, 2, 3));
        assertEquals(map.get(2), 3);
        assertTrue(map.replace(2, 3, 4));
        assertEquals(map.get(2), 4);
    }

    @Test
    public void testMapLockAndUnlockAndTryLock() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapLockAndUnlockAndTryLock");
        map.lock("key0");
        map.lock("key1");
        map.lock("key2");
        map.lock("key3");
        final AtomicBoolean check1 = new AtomicBoolean(false);
        final AtomicBoolean check2 = new AtomicBoolean(false);
        final CountDownLatch latch0 = new CountDownLatch(1);
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final CountDownLatch latch3 = new CountDownLatch(1);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    check1.set(map.tryLock("key0"));
                    check2.set(map.tryLock("key0", 3000, TimeUnit.MILLISECONDS));
                    latch0.countDown();

                    map.put("key1", "value1");
                    latch1.countDown();

                    map.put("key2", "value2");
                    latch2.countDown();

                    map.put("key3", "value3");
                    latch3.countDown();
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        });
        thread.start();

        Thread.sleep(1000);
        map.unlock("key0");

        assertTrue(latch0.await(3, TimeUnit.SECONDS));
        assertFalse(check1.get());
        assertTrue(check2.get());

        map.unlock("key1");
        assertTrue(latch1.await(3, TimeUnit.SECONDS));
        map.unlock("key2");
        assertTrue(latch2.await(3, TimeUnit.SECONDS));
        map.unlock("key3");
        assertTrue(latch3.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void testMapIsLocked() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapIsLocked");
        map.lock("key1");
        assertTrue(map.isLocked("key1"));
        assertFalse(map.isLocked("key2"));

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean b1 = new AtomicBoolean();
        final AtomicBoolean b2 = new AtomicBoolean();
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    b1.set(map.isLocked("key1"));
                    b2.set(map.isLocked("key2"));
                    latch.countDown();
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        });
        thread.start();
        latch.await();
        assertTrue(b1.get());
        assertFalse(b2.get());
        thread.join();
    }

    @Test
    public void testEntrySet() {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testEntrySet");
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        map.put(4, 4);
        map.put(5, 5);
        Set<Map.Entry> entrySet = new HashSet<Map.Entry>();
        entrySet.add(new AbstractMap.SimpleImmutableEntry(1, 1));
        entrySet.add(new AbstractMap.SimpleImmutableEntry(2, 2));
        entrySet.add(new AbstractMap.SimpleImmutableEntry(3, 3));
        entrySet.add(new AbstractMap.SimpleImmutableEntry(4, 4));
        entrySet.add(new AbstractMap.SimpleImmutableEntry(5, 5));
        assertEquals(entrySet, map.entrySet());
    }

    @Test
    public void testEntryView() throws Exception {
        Config config = new Config();
        config.getReplicatedMapConfig("default").setStatisticsEnabled(true);
        final IReplicatedMap<Integer, Integer> map = getReplicatedMap("testEntryView");
        long time1 = Clock.currentTimeMillis();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        long time2 = Clock.currentTimeMillis();
        map.get(3);
        map.get(3);
        long time3 = Clock.currentTimeMillis();
        map.put(2, 22);
        
        EntryView<Integer, Integer> entryView1 = map.getEntryView(1);
        EntryView<Integer, Integer> entryView2 = map.getEntryView(2);
        EntryView<Integer, Integer> entryView3 = map.getEntryView(3);


        assertEquals((Integer) 1, entryView1.getKey());
        assertEquals((Integer) 2, entryView2.getKey());
        assertEquals((Integer) 3, entryView3.getKey());

        assertEquals((Integer) 1, entryView1.getValue());
        assertEquals((Integer) 22, entryView2.getValue());
        assertEquals((Integer) 3, entryView3.getValue());

        assertEquals(0, entryView1.getHits());
        assertEquals(1, entryView2.getHits());
        assertEquals(2, entryView3.getHits());

        assertEquals(0, entryView1.getVersion());
        assertEquals(1, entryView2.getVersion());
        assertEquals(0, entryView3.getVersion());

        assertTrue(entryView1.getCreationTime() >= time1 && entryView1.getCreationTime() <= time2);
        assertTrue(entryView2.getCreationTime() >= time1 && entryView2.getCreationTime() <= time2);
        assertTrue(entryView3.getCreationTime() >= time1 && entryView3.getCreationTime() <= time2);

        assertTrue(entryView1.getLastAccessTime() >= time1 && entryView1.getLastAccessTime() <= time2);
        assertTrue(entryView2.getLastAccessTime() >= time3);
        assertTrue(entryView3.getLastAccessTime() >= time2 && entryView3.getLastAccessTime() <= time3);

        assertTrue(entryView1.getLastUpdateTime() >= time1 && entryView1.getLastUpdateTime() <= time2);
        assertTrue(entryView2.getLastUpdateTime() >= time3);
        assertTrue(entryView3.getLastUpdateTime() >= time1 && entryView3.getLastUpdateTime() <= time2);

    }

    @Test
    public void testMapTryPut() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapTryPut");
        final String key1 = "key1";
        final String key2 = "key2";
        map.lock(key1);
        final AtomicInteger counter = new AtomicInteger(6);
        final CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (map.tryPut(key1, "value1", 100, TimeUnit.MILLISECONDS) == false)
                        counter.decrementAndGet();

                    if(map.get(key1) == null)
                        counter.decrementAndGet();

                    if(map.tryPut(key2, "value", 100, TimeUnit.MILLISECONDS))
                        counter.decrementAndGet();

                    if(map.get(key2).equals("value"))
                        counter.decrementAndGet();

                    if(map.tryPut(key1, "value1", 5, TimeUnit.SECONDS))
                        counter.decrementAndGet();

                    if(map.get(key1).equals("value1"))
                        counter.decrementAndGet();

                    latch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        });
        thread.start();
        Thread.sleep(1000);
        map.unlock("key1");
        latch.await(10, TimeUnit.SECONDS);
        assertEquals(0, counter.get());
        thread.join(10000);
    }

    @Test
    public void testGetPutRemoveAsync() {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testGetPutRemoveAsync");
        Future<Object> ff = map.putAsync(1, 1);
        try {
            assertEquals(null, ff.get());
            assertEquals(1, map.putAsync(1, 2).get());
            assertEquals(2, map.getAsync(1).get());
            assertEquals(2, map.removeAsync(1).get());
            assertEquals(0, map.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGetAllPutAll() throws InterruptedException {
        warmUpPartitions(instances);
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testGetAllPutAll");
        Set ss = new HashSet();
        ss.add(1);
        ss.add(3);
        map.getAll(ss);
        assertTrue(map.isEmpty());

        Map mm = new HashMap();
        int size = 100;
        for (int i = 0; i < size; i++) {
            mm.put(i, i);
        }
        map.putAll(mm);
        assertEquals(size, map.size());
        for (int i = 0; i < size; i++) {
            assertEquals(map.get(i), i);
        }

        size = 10000;
        for (int i = 0; i < size; i++) {
            mm.put(i, i);
        }
        map.putAll(mm);
        assertEquals(size, map.size());
        for (int i = 0; i < size; i++) {
            assertEquals(map.get(i), i);
        }

        ss = new HashSet();
        ss.add(1);
        ss.add(3);
        Map m2 = map.getAll(ss);
        assertEquals(m2.size(), 2);
        assertEquals(m2.get(1), 1);
        assertEquals(m2.get(3), 3);
    }

    @Test
    // todo fails in parallel
    public void testPutAllBackup() throws InterruptedException {
        HazelcastInstance instance1 = instances[0];
        HazelcastInstance instance2 = instances[1];
        final IReplicatedMap<Object, Object> map = instance1.getReplicatedMap("testPutAllBackup");
        final IReplicatedMap<Object, Object> map2 = instance2.getReplicatedMap("testPutAllBackup");
        warmUpPartitions(instances);

        Map mm = new HashMap();
        final int size = 100;
        for (int i = 0; i < size; i++) {
            mm.put(i, i);
        }
        map.putAll(mm);
        assertEquals(size, map.size());
        for (int i = 0; i < size; i++) {
            assertEquals(i, map.get(i));
        }

        instance2.getLifecycleService().shutdown();
        assertEquals(size, map.size());
        for (int i = 0; i < size; i++) {
            assertEquals(i, map.get(i));
        }
    }

    @Test
    public void testMapListenersWithValue() throws Exception {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapListenersWithValue");
        final AtomicReference<Object> addedKey = new AtomicReference<Object>();
        final AtomicReference<Object>  addedValue = new AtomicReference<Object>();
        final AtomicReference<Object>  updatedKey = new AtomicReference<Object>();
        final AtomicReference<Object>  oldValue = new AtomicReference<Object>();
        final AtomicReference<Object>  newValue = new AtomicReference<Object>();
        final AtomicReference<Object>  removedKey = new AtomicReference<Object>();
        final AtomicReference<Object>  removedValue = new AtomicReference<Object>();

        final CountDownLatch[] latches = new CountDownLatch[3];
        latches[0] = new CountDownLatch(1);
        latches[1] = new CountDownLatch(1);
        latches[2] = new CountDownLatch(1);
        
        EntryListener<Object, Object> listener = new EntryListener<Object, Object>() {
            public void entryAdded(EntryEvent<Object, Object> event) {
            	System.out.println("Add: " + event);
            	addedKey.set(event.getKey());
                addedValue.set(event.getValue());
                latches[0].countDown();
            }

            public void entryUpdated(EntryEvent<Object, Object> event) {
            	System.out.println("Update: " + event);
                updatedKey.set(event.getKey());
                oldValue.set(event.getOldValue());
                newValue.set(event.getValue());
                if (latches[0].getCount() > 0) {
                	throw new RuntimeException();
                }
                latches[1].countDown();
            }

            public void entryRemoved(EntryEvent<Object, Object> event) {
            	System.out.println("Remove: " + event);
                removedKey.set(event.getKey());
                removedValue.set(event.getValue());
                if (event.getValue() == null) {
                	throw new RuntimeException();
                }
                latches[2].countDown();
            }

            public void entryEvicted(EntryEvent<Object, Object> event) {
            }
        };
        map.addEntryListener(listener, true);
        map.put("key", "value");
        latches[0].await(10, TimeUnit.SECONDS);

        map.put("key", "value2");
        latches[1].await(10, TimeUnit.SECONDS);

        map.remove("key");
        latches[2].await(10, TimeUnit.SECONDS);
        
        assertEquals(addedKey.get(), "key");
        assertEquals(addedValue.get(), "value");
        assertEquals(updatedKey.get(), "key");
        assertEquals(oldValue.get(), "value");
        assertEquals(newValue.get(), "value2");
        assertEquals(removedKey.get(), "key");
        assertEquals(removedValue.get(), "value2");
    }


    @Test
    public void testMapQueryListener() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapQueryListener");
        final Object[] addedKey = new Object[1];
        final Object[] addedValue = new Object[1];
        final Object[] updatedKey = new Object[1];
        final Object[] oldValue = new Object[1];
        final Object[] newValue = new Object[1];
        final Object[] removedKey = new Object[1];
        final Object[] removedValue = new Object[1];

        EntryListener<Object, Object> listener = new EntryListener<Object, Object>() {
            public void entryAdded(EntryEvent<Object, Object> event) {
                addedKey[0] = event.getKey();
                addedValue[0] = event.getValue();
            }

            public void entryRemoved(EntryEvent<Object, Object> event) {
                removedKey[0] = event.getKey();
                removedValue[0] = event.getOldValue();
            }

            public void entryUpdated(EntryEvent<Object, Object> event) {
                updatedKey[0] = event.getKey();
                oldValue[0] = event.getOldValue();
                newValue[0] = event.getValue();
            }

            public void entryEvicted(EntryEvent<Object, Object> event) {
            }
        };

        map.addEntryListener(listener, new StartsWithPredicate("a"), null, true);
        map.put("key1", "abc");
        map.put("key2", "bcd");
        map.put("key2", "axyz");
        map.remove("key1");
        Thread.sleep(1000);

        assertEquals(addedKey[0], "key1");
        assertEquals(addedValue[0], "abc");
        assertEquals(updatedKey[0], "key2");
        assertEquals(oldValue[0], "bcd");
        assertEquals(newValue[0], "axyz");
        assertEquals(removedKey[0], "key1");
        assertEquals(removedValue[0], "abc");
    }

    static class StartsWithPredicate implements Predicate<Object, Object>, Serializable {
        String pref;

        StartsWithPredicate(String pref) {
            this.pref = pref;
        }

        public boolean apply(Map.Entry<Object, Object> mapEntry) {
            String val = (String) mapEntry.getValue();
            if (val == null)
                return false;
            if (val.startsWith(pref))
                return true;
            return false;
        }
    }

    @Test
    public void testMapListenersWithValueAndKeyFiltered() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapListenersWithValueAndKeyFiltered");
        final Object[] addedKey = new Object[1];
        final Object[] addedValue = new Object[1];
        final Object[] updatedKey = new Object[1];
        final Object[] oldValue = new Object[1];
        final Object[] newValue = new Object[1];
        final Object[] removedKey = new Object[1];
        final Object[] removedValue = new Object[1];

        EntryListener<Object, Object> listener = new EntryListener<Object, Object>() {
            public void entryAdded(EntryEvent<Object, Object> event) {
                addedKey[0] = event.getKey();
                addedValue[0] = event.getValue();
            }

            public void entryRemoved(EntryEvent<Object, Object> event) {
                removedKey[0] = event.getKey();
                removedValue[0] = event.getOldValue();
            }

            public void entryUpdated(EntryEvent<Object, Object> event) {
                updatedKey[0] = event.getKey();
                oldValue[0] = event.getOldValue();
                newValue[0] = event.getValue();
            }

            public void entryEvicted(EntryEvent<Object, Object> event) {
            }
        };
        map.addEntryListener(listener, "key", true);
        map.put("keyx", "valuex");
        map.put("key", "value");
        map.put("key", "value2");
        map.put("keyx", "valuex2");
        map.put("keyz", "valuez");
        map.remove("keyx");
        map.remove("key");
        map.remove("keyz");
        Thread.sleep(1000);

        assertEquals(addedKey[0], "key");
        assertEquals(addedValue[0], "value");
        assertEquals(updatedKey[0], "key");
        assertEquals(oldValue[0], "value");
        assertEquals(newValue[0], "value2");
        assertEquals(removedKey[0], "key");
        assertEquals(removedValue[0], "value2");
    }


    @Test
    public void testMapListenersWithoutValue() throws InterruptedException {
        final IReplicatedMap<Object, Object> map = getReplicatedMap("testMapListenersWithoutValue");
        final Object[] addedKey = new Object[1];
        final Object[] addedValue = new Object[1];
        final Object[] updatedKey = new Object[1];
        final Object[] oldValue = new Object[1];
        final Object[] newValue = new Object[1];
        final Object[] removedKey = new Object[1];
        final Object[] removedValue = new Object[1];

        EntryListener<Object, Object> listener = new EntryListener<Object, Object>() {
            public void entryAdded(EntryEvent<Object, Object> event) {
                addedKey[0] = event.getKey();
                addedValue[0] = event.getValue();
            }

            public void entryRemoved(EntryEvent<Object, Object> event) {
                removedKey[0] = event.getKey();
                removedValue[0] = event.getOldValue();
            }

            public void entryUpdated(EntryEvent<Object, Object> event) {
                updatedKey[0] = event.getKey();
                oldValue[0] = event.getOldValue();
                newValue[0] = event.getValue();
            }

            public void entryEvicted(EntryEvent<Object, Object> event) {
            }
        };
        map.addEntryListener(listener, false);
        map.put("key", "value");
        map.put("key", "value2");
        map.remove("key");
        Thread.sleep(1000);

        assertEquals(addedKey[0], "key");
        assertEquals(addedValue[0], null);
        assertEquals(updatedKey[0], "key");
        assertEquals(oldValue[0], null);
        assertEquals(newValue[0], null);
        assertEquals(removedKey[0], "key");
        assertEquals(removedValue[0], null);
    }

    @Test
    @Ignore(value="TTL / eviction not yet supported on ReplicatedMap")
    public void testPutWithTtl() throws InterruptedException {
        IReplicatedMap<String, String> map = getReplicatedMap("testPutWithTtl");
        final CountDownLatch latch = new CountDownLatch(1);
        map.addEntryListener(new EntryAdapter<String, String>() {
            public void entryEvicted(EntryEvent<String, String> event) {
                latch.countDown();
            }
        }, true);

        map.put("key", "value", 3, TimeUnit.SECONDS);
        assertEquals("value", map.get("key"));

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNull(map.get("key"));
    }

    @Test
    public void testMapEntryProcessor() throws InterruptedException {
        IReplicatedMap<Integer, Integer> map = getReplicatedMap("testMapEntryProcessor");
        map.put(1, 1);
        EntryProcessor entryProcessor = new SampleEntryProcessor();
        map.executeOnKey(1, entryProcessor);
        assertEquals(map.get(1), (Object) 2);
    }
    
    static class SampleEntryProcessor implements EntryProcessor, EntryBackupProcessor, Serializable {

        public Object process(Map.Entry entry) {
            entry.setValue((Integer) entry.getValue() + 1);
            return true;
        }

        public EntryBackupProcessor getBackupProcessor() {
            return SampleEntryProcessor.this;
        }

        public void processBackup(Map.Entry entry) {
            entry.setValue((Integer) entry.getValue() + 1);
        }
    }

    public static class SampleIndexableObject implements Serializable {
    	String name;
    	Integer value;
    	
    	public SampleIndexableObject() {
    	}
    	
    	public SampleIndexableObject(String name, Integer value) {
    		this.name = name;
    		this.value = value;
    	}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getValue() {
			return value;
		}

		public void setValue(Integer value) {
			this.value = value;
		}
    }
    
    public static class SampleIndexableObjectMapLoader implements MapLoader<Integer, SampleIndexableObject>, MapStoreFactory<Integer, SampleIndexableObject> {

    	private SampleIndexableObject[] values = new SampleIndexableObject[10];
    	private Set<Integer> keys = new HashSet<Integer>();
    	
    	boolean preloadValues = false;
    	
    	public SampleIndexableObjectMapLoader() {
	    	for (int i = 0; i < 10; i++) {
	    		keys.add(i);
	    		values[i] = new SampleIndexableObject("My-" + i, i);
	    	}
    	}
    	
		@Override
		public SampleIndexableObject load(Integer key) {
			if (!preloadValues) return null;
			return values[key];
		}

		@Override
		public Map<Integer, SampleIndexableObject> loadAll(Collection<Integer> keys) {
			if (!preloadValues) return Collections.emptyMap();
			Map<Integer, SampleIndexableObject> data = new HashMap<Integer, SampleIndexableObject>();
			for (Integer key : keys) {
				data.put(key, values[key]);
			}
			return data;
		}

		@Override
		public Set<Integer> loadAllKeys() {
			if (!preloadValues) return Collections.emptySet();
			return Collections.unmodifiableSet(keys);
		}

		@Override
		public MapLoader<Integer, SampleIndexableObject> newMapStore(String mapName, Properties properties) {
			return this;
		}
    }

}
