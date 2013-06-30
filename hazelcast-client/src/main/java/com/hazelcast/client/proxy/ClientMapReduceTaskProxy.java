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

import java.util.Map;

import com.hazelcast.client.spi.ClientContext;
import com.hazelcast.client.spi.ClientExecutionService;
import com.hazelcast.client.spi.ClientInvocationService;
import com.hazelcast.core.Collator;
import com.hazelcast.core.MapReduceCollatorListener;
import com.hazelcast.core.MapReduceListener;
import com.hazelcast.map.client.MapReduceRequest;
import com.hazelcast.map.mapreduce.AbstractMapReduceTask;
import com.hazelcast.util.ExceptionUtil;

public class ClientMapReduceTaskProxy<KeyIn, ValueIn, KeyOut, ValueOut> extends AbstractMapReduceTask<KeyIn, ValueIn, KeyOut, ValueOut> {

	private final ClientContext context;

	ClientMapReduceTaskProxy(String name, ClientContext context) {
		super(name);
		this.context = context;
	}

	@Override
	protected Map<Integer, Object> invokeTasks() throws Exception {
		ClientInvocationService cis = context.getInvocationService();
		MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut> request;
		request = new MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut>(name, mapper, reducer);
		return cis.invokeOnRandomTarget(request);
	}

	@Override
	protected <R> MapReduceBackgroundTask<R> buildMapReduceBackgroundTask(MapReduceListener<KeyOut, ValueOut> listener) {
		return new ClientMapReduceBackgroundTask(listener);
	}

	@Override
	protected <R> MapReduceBackgroundTask<R> buildMapReduceBackgroundTask(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> collatorListener) {
		return new ClientMapReduceBackgroundTask(collator, collatorListener);
	}

	@Override
	protected <R> void invokeAsyncTask(MapReduceBackgroundTask<R> task) {
		ClientExecutionService es = context.getExecutionService();
		es.execute(task);
	}

	private class ClientMapReduceBackgroundTask<R> extends MapReduceBackgroundTask<R> {
		
		private ClientMapReduceBackgroundTask(MapReduceListener<KeyOut, ValueOut> listener) {
			super(listener);
		}
		
		private ClientMapReduceBackgroundTask(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> collatorListener) {
			super(collator, collatorListener);
		}

		@Override
		public void run() {
			ClientInvocationService cis = context.getInvocationService();
			MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut> request = new MapReduceRequest<KeyIn, ValueIn, KeyOut, ValueOut>();
			try {
				Map<Integer, Object> responses = cis.invokeOnRandomTarget(request);
                Map groupedResponses = groupResponsesByKey(responses);
                Map reducedResults = finalReduceStep(groupedResponses);
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
