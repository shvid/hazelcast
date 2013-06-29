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

package com.hazelcast.map.proxy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

import com.hazelcast.core.Collator;
import com.hazelcast.core.MapReduceCollatorListener;
import com.hazelcast.core.MapReduceListener;
import com.hazelcast.core.MapReduceTask;
import com.hazelcast.core.Reducer;
import com.hazelcast.map.MapService;
import com.hazelcast.map.mapreduce.Mapper;
import com.hazelcast.map.operation.MapReduceOperation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.OperationService;
import com.hazelcast.spi.impl.BinaryOperationFactory;
import com.hazelcast.util.ExceptionUtil;

public class MapReduceTaskImpl<KeyIn, ValueIn, KeyOut, ValueOut> implements MapReduceTask<KeyIn, ValueIn, KeyOut, ValueOut> {

	private final NodeEngine nodeEngine;
	private final String name;
	
	private Mapper<KeyIn, ValueIn, KeyOut, ValueOut> mapper;
	private Reducer<KeyOut, ValueOut> reducer;

	public MapReduceTaskImpl(String name, NodeEngine nodeEngine) {
		this.name = name;
		this.nodeEngine = nodeEngine;
	}

	@Override
	public MapReduceTask<KeyIn, ValueIn, KeyOut, ValueOut> mapper(Mapper<KeyIn, ValueIn, KeyOut, ValueOut> mapper) {
		if (mapper != null)
			throw new IllegalStateException("mapper must not be null");
		if (this.mapper != null)
			throw new IllegalStateException("mapper already set");
		this.mapper = mapper;
		return this;
	}

	@Override
	public MapReduceTask<KeyIn, ValueIn, KeyOut, ValueOut> reducer(Reducer<KeyOut, ValueOut> reducer) {
		if (reducer != null)
			throw new IllegalStateException("reducer must not be null");
		if (this.reducer != null)
			throw new IllegalStateException("reducer already set");
		this.reducer = reducer;
		return this;
	}

	@Override
	public Map<KeyOut, ValueOut> submit() {
		OperationService os = nodeEngine.getOperationService();
		MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut> operation;
		operation = new MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut>(name, mapper, reducer);
		operation.setNodeEngine(nodeEngine).setCallerUuid(nodeEngine.getLocalMember().getUuid());
		try {
			Map<Integer, Object> response = os.invokeOnAllPartitions(MapService.SERVICE_NAME, new BinaryOperationFactory(operation, nodeEngine));
			Map<KeyOut, ValueOut> reducedResults = new HashMap<KeyOut, ValueOut>();
			for (Entry<Integer, Object> entry : response.entrySet()) {
				reducedResults.putAll((Map<KeyOut, ValueOut>) entry.getValue());
			}
			return reducedResults;
		} catch (Throwable t) {
			ExceptionUtil.rethrow(t);
		}
		return Collections.emptyMap();
	}

	@Override
	public <R> R submit(Collator<KeyOut, ValueOut, R> collator) {
		Map<KeyOut, ValueOut> reducedResults = submit();
		return collator.collate(reducedResults);
	}

	@Override
	public void submitAsync(MapReduceListener<KeyOut, ValueOut> listener) {
		ExecutorService es = nodeEngine.getExecutionService().getExecutor("hz:query");
		es.execute(new MapReduceBackgroundTask(listener));
	}

	@Override
	public <R> void submitAsync(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> listener) {
		ExecutorService es = nodeEngine.getExecutionService().getExecutor("hz:query");
		es.execute(new MapReduceBackgroundTask<R>(collator, listener));
	}
	
	private class MapReduceBackgroundTask<R> implements Runnable {

		private final MapReduceListener<KeyOut, ValueOut> listener;
		private final MapReduceCollatorListener<R> collatorListener;
		private final Collator<KeyOut, ValueOut, R> collator;
		
		private MapReduceBackgroundTask(MapReduceListener<KeyOut, ValueOut> listener) {
			this.listener = listener;
			this.collator = null;
			this.collatorListener = null;
		}
		
		private MapReduceBackgroundTask(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> collatorListener) {
			this.collator = collator;
			this.collatorListener = collatorListener;
			this.listener = null;
		}
		
		@Override
		public void run() {
			OperationService os = nodeEngine.getOperationService();
			MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut> operation;
			operation = new MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut>(name, mapper, reducer);
			operation.setNodeEngine(nodeEngine).setCallerUuid(nodeEngine.getLocalMember().getUuid());
			try {
				Map<Integer, Object> response = os.invokeOnAllPartitions(MapService.SERVICE_NAME, new BinaryOperationFactory(operation, nodeEngine));
				Map<KeyOut, ValueOut> reducedResults = new HashMap<KeyOut, ValueOut>();
				for (Entry<Integer, Object> entry : response.entrySet()) {
					reducedResults.putAll((Map<KeyOut, ValueOut>) entry.getValue());
				}
				if (collator == null) {
					listener.onCompletion(reducedResults);
				} else {
					R result = collator.collate(reducedResults);
					collatorListener.onCompletion(result);
				}
			} catch (Throwable t) {
				ExceptionUtil.rethrow(t);
			}
		}
	}
}
