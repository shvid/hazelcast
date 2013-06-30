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

package com.hazelcast.client.map.mapreduce;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.Config;
import com.hazelcast.core.Collator;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapReduceTask;
import com.hazelcast.map.mapreduce.Collector;
import com.hazelcast.map.mapreduce.Mapper;
import com.hazelcast.map.mapreduce.Reducer;
import com.hazelcast.test.HazelcastJUnit4ClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.SerialTest;

@RunWith(HazelcastJUnit4ClassRunner.class)
@Category(SerialTest.class)
public class ClientMapReduceTest extends HazelcastTestSupport {

    private static final String MAP_NAME = "default";

    @Before
    public void gc() {
        Runtime.getRuntime().gc();
    }

    @After
    public void cleanup() {
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
    }

    @Test(timeout = 20000)
    public void testMapper() throws Exception {
        HazelcastInstance h1 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h2 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h3 = Hazelcast.newHazelcastInstance();

        HazelcastInstance client = HazelcastClient.newHazelcastClient(null);
        IMap<Integer, Integer> m1 = client.getMap(MAP_NAME);
        for (int i = 0; i < 100; i++) {
            m1.put(i, i);
        }

        MapReduceTask<Integer, Integer, String, Integer> task = m1.buildMapReduceTask();
        Map<String, List<Integer>> result = task.mapper(new TestMapper()).submit();
        assertEquals(100, result.size());
        for (List<Integer> value : result.values()) {
            assertEquals(1, value.size());
        }
    }

    @Test(timeout = 20000)
    public void testMapperReducer() throws Exception {
        HazelcastInstance h1 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h2 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h3 = Hazelcast.newHazelcastInstance();

        HazelcastInstance client = HazelcastClient.newHazelcastClient(null);
        IMap<Integer, Integer> m1 = client.getMap(MAP_NAME);
        for (int i = 0; i < 100; i++) {
            m1.put(i, i);
        }

        MapReduceTask<Integer, Integer, String, Integer> task = m1.buildMapReduceTask();
        Map<String, Integer> result = task.mapper(new GroupingTestMapper()).reducer(new TestReducer()).submit();

        // Precalculate results
        int[] expectedResults = new int[4];
        for (int i = 0; i < 100; i++) {
            int index = i % 4;
            expectedResults[index] += i;
        }

        for (int i = 0; i < 4; i++) {
            assertEquals(expectedResults[i], (int) result.get(String.valueOf(i)));
        }
    }

    @Test(timeout = 20000)
    public void testMapperCollator() throws Exception {
        HazelcastInstance h1 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h2 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h3 = Hazelcast.newHazelcastInstance();

        HazelcastInstance client = HazelcastClient.newHazelcastClient(null);
        IMap<Integer, Integer> m1 = client.getMap(MAP_NAME);
        for (int i = 0; i < 100; i++) {
            m1.put(i, i);
        }

        MapReduceTask<Integer, Integer, String, Integer> task = m1.buildMapReduceTask();
        int result = task.mapper(new GroupingTestMapper()).submit(new GroupingTestCollator());

        // Precalculate result
        int expectedResult = 0;
        for (int i = 0; i < 100; i++) {
            expectedResult += i;
        }

        for (int i = 0; i < 4; i++) {
            assertEquals(expectedResult, result);
        }
    }

    @Test(timeout = 20000)
    public void testMapperReducerCollator() throws Exception {
        HazelcastInstance h1 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h2 = Hazelcast.newHazelcastInstance();
        HazelcastInstance h3 = Hazelcast.newHazelcastInstance();

        HazelcastInstance client = HazelcastClient.newHazelcastClient(null);
        IMap<Integer, Integer> m1 = client.getMap(MAP_NAME);
        for (int i = 0; i < 100; i++) {
            m1.put(i, i);
        }

        MapReduceTask<Integer, Integer, String, Integer> task = m1.buildMapReduceTask();
        int result = task.mapper(new GroupingTestMapper()).reducer(new TestReducer()).submit(new TestCollator());

        // Precalculate result
        int expectedResult = 0;
        for (int i = 0; i < 100; i++) {
            expectedResult += i;
        }

        for (int i = 0; i < 4; i++) {
            assertEquals(expectedResult, result);
        }
    }

    public static class TestMapper implements Mapper<Integer, Integer, String, Integer> {

        @Override
        public void map(Integer key, Integer value, Collector<String, Integer> collector) {
            collector.emit(String.valueOf(key), value);
        }
    }

    public static class GroupingTestMapper implements Mapper<Integer, Integer, String, Integer> {

        @Override
        public void map(Integer key, Integer value, Collector<String, Integer> collector) {
            collector.emit(String.valueOf(key % 4), value);
        }
    }

    public static class TestReducer implements Reducer<String, Integer> {

        @Override
        public Integer reduce(String key, Iterator<Integer> values) {
            int sum = 0;
            while (values.hasNext()) {
                sum += values.next();
            }
            return sum;
        }
    }

    public static class GroupingTestCollator implements Collator<String, List<Integer>, Integer> {

        @Override
        public Integer collate(Map<String, List<Integer>> reducedResults) {
            int sum = 0;
            for (List<Integer> values : reducedResults.values()) {
                for (Integer value : values) {
                    sum += value;
                }
            }
            return sum;
        }

    }

    public static class TestCollator implements Collator<String, Integer, Integer> {

        @Override
        public Integer collate(Map<String, Integer> reducedResults) {
            int sum = 0;
            for (Integer value : reducedResults.values()) {
                sum += value;
            }
            return sum;
        }

    }

}
