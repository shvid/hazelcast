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

package com.hazelcast.core;

public interface BaseMap<K, V> extends DistributedObject {

    boolean containsKey(Object key);

    V get(Object key);

    V put(K key, V value);

    void set(K key, V value);

    V putIfAbsent(K key, V value);

    V replace(K key, V value);

    boolean replace(K key, V oldValue, V newValue);

    V remove(Object key);

    void delete(Object key);

    boolean remove(Object key, Object value);

    int size();
}
