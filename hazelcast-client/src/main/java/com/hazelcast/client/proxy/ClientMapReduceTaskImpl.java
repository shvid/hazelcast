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

package com.hazelcast.client.proxy;

import java.util.Collections;
import java.util.Map;

import com.hazelcast.client.spi.ClientContext;
import com.hazelcast.client.spi.ClientExecutionService;
import com.hazelcast.client.spi.ClientInvocationService;
import com.hazelcast.core.Collator;
import com.hazelcast.core.MapReduceCollatorListener;
import com.hazelcast.core.MapReduceListener;
import com.hazelcast.core.MapReduceTask;
import com.hazelcast.map.client.MapReduceRequest;
import com.hazelcast.map.mapreduce.Mapper;
import com.hazelcast.map.mapreduce.Reducer;
import com.hazelcast.util.ExceptionUtil;

public class ClientMapReduceTaskImpl<KeyIn, ValueIn, KeyOut, ValueOut> implements MapReduceTask<KeyIn, ValueIn, KeyOut, ValueOut> {

	private final ClientContext context;
	private final String name;
	
	private Mapper<KeyIn, ValueIn, KeyOut, ValueOut> mapper;
	private Reducer<KeyOut, ValueOut> reducer;

	ClientMapReduceTaskImpl(String name, ClientContext context) {
		this.context = context;
		this.name = name;
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
		ClientInvocationService cis = context.getInvocationService();
		MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut> request;
		request = new MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut>(name, mapper, reducer);
		try {
			return (Map<KeyOut, ValueOut>) cis.invokeOnRandomTarget(request);
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
		ClientExecutionService es = context.getExecutionService();
		es.execute(new ClientMapReduceBackgroundTask(listener));
	}

	@Override
	public <R> void submitAsync(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> listener) {
		ClientExecutionService es = context.getExecutionService();
		es.execute(new ClientMapReduceBackgroundTask<R>(collator, listener));
	}

	private class ClientMapReduceBackgroundTask<R> implements Runnable {
		private final MapReduceListener<KeyOut, ValueOut> listener;
		private final MapReduceCollatorListener<R> collatorListener;
		private final Collator<KeyOut, ValueOut, R> collator;
		
		private ClientMapReduceBackgroundTask(MapReduceListener<KeyOut, ValueOut> listener) {
			this.listener = listener;
			this.collator = null;
			this.collatorListener = null;
		}
		
		private ClientMapReduceBackgroundTask(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> collatorListener) {
			this.collator = collator;
			this.collatorListener = collatorListener;
			this.listener = null;
		}

		@Override
		public void run() {
			ClientInvocationService cis = context.getInvocationService();
			MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut> request = new MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut>();
			try {
				Map<KeyOut, ValueOut> reducedResults = (Map<KeyOut, ValueOut>) cis.invokeOnRandomTarget(request);
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
