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

import static com.hazelcast.cluster.MemberAttributeChangedOperation.DELTA_MEMBER_PROPERTIES_OP_PUT;
import static com.hazelcast.cluster.MemberAttributeChangedOperation.DELTA_MEMBER_PROPERTIES_OP_REMOVE;

import java.io.IOException;
import java.util.Set;

import com.hazelcast.map.operation.MapOperationType;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

public class MemberAttributeEvent extends MembershipEvent implements DataSerializable {

	private MapOperationType operationType;
	private String key; 
	private Object value;
	
	private Member member;
	
	public MemberAttributeEvent(Cluster cluster, Member member, Set<Member> members, MapOperationType operationType, String key, Object value) {
		super(cluster, member, MEMBER_ATTRIBUTE_CHANGED, members);
		this.member = member;
	}

	public MapOperationType getOperationType() {
		return operationType;
	}

	public String getKey() {
		return key;
	}

	public Object getValue() {
		return value;
	}

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
	    out.writeObject(member);
		out.writeUTF(key);
		switch (operationType) {
			case PUT:
				out.writeByte(DELTA_MEMBER_PROPERTIES_OP_PUT);
				out.writeObject(value);
				break;
			case REMOVE:
				out.writeByte(DELTA_MEMBER_PROPERTIES_OP_REMOVE);
				break;
		}
	}

	@Override
	public void readData(ObjectDataInput in) throws IOException {
		member = in.readObject();
		key = in.readUTF();
		int operation = in.readByte();
		switch (operation)
		{
			case DELTA_MEMBER_PROPERTIES_OP_PUT:
				operationType = MapOperationType.PUT;
				value = in.readObject();
				break;
			case DELTA_MEMBER_PROPERTIES_OP_REMOVE:
				operationType = MapOperationType.REMOVE;
				break;
		}
	}
	
}
