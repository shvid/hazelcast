/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client.txn;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MultiMap;
import com.hazelcast.core.TransactionalMultiMap;
import com.hazelcast.test.HazelcastJUnit4ClassRunner;
import com.hazelcast.test.annotation.SerialTest;
import com.hazelcast.transaction.TransactionContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @ali 6/10/13
 */
@RunWith(HazelcastJUnit4ClassRunner.class)
@Category(SerialTest.class)
public class ClientTxnMultiMapTest {

    static final String name = "test";
    static HazelcastInstance hz;
    static HazelcastInstance server;
    static HazelcastInstance second;

    @BeforeClass
    public static void init(){
        server = Hazelcast.newHazelcastInstance();
//        second = Hazelcast.newHazelcastInstance();
        hz = HazelcastClient.newHazelcastClient(null);
    }

    @AfterClass
    public static void destroy() {
        hz.getLifecycleService().shutdown();
        Hazelcast.shutdownAll();
    }

    @Test
    public void testPutGetRemove() throws Exception {
        final MultiMap mm = hz.getMultiMap(name);
        mm.put("key1", "value1");

        final TransactionContext context = hz.newTransactionContext();
        context.beginTransaction();
        final TransactionalMultiMap multiMap = context.getMultiMap(name);
        assertFalse(multiMap.put("key1", "value1"));
        assertTrue(multiMap.put("key1", "value2"));
        assertEquals(2, multiMap.size());

        context.commitTransaction();

        assertEquals(2, mm.size());

    }
}
