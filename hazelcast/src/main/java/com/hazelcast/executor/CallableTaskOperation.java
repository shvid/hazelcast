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

package com.hazelcast.executor;

import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.util.concurrent.Callable;

/**
 * @mdogan 1/18/13
 */
public final class CallableTaskOperation extends BaseCallableTaskOperation implements IdentifiedDataSerializable {

    public CallableTaskOperation() {
    }

    public CallableTaskOperation(String name, String uuid, Callable callable) {
        super(name, uuid, callable);
    }

    public int getFactoryId() {
        return ExecutorDataSerializerHook.F_ID;
    }

    public int getId() {
        return ExecutorDataSerializerHook.CALLABLE_TASK;
    }
}
