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

package com.hazelcast.nio;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * @mdogan 12/28/12
 */
public interface ObjectDataOutput extends DataOutput {

    void writeCharArray(char[] chars) throws IOException;

    void writeIntArray(int[] ints) throws IOException;

    void writeLongArray(long[] longs) throws IOException;

    void writeDoubleArray(double[] values) throws IOException;

    void writeFloatArray(float[] values) throws IOException;

    void writeShortArray(short[] values) throws IOException;

    void writeObject(Object object) throws IOException;

    byte[] toByteArray();

    ByteOrder getByteOrder();
}
