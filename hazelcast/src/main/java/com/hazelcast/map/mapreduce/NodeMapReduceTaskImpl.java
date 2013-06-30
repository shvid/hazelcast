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

package com.hazelcast.map.mapreduce;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.hazelcast.core.Collator;
import com.hazelcast.core.MapReduceCollatorListener;
import com.hazelcast.core.MapReduceListener;
import com.hazelcast.map.MapService;
import com.hazelcast.map.operation.MapReduceOperation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.OperationService;
import com.hazelcast.spi.impl.BinaryOperationFactory;
import com.hazelcast.util.ExceptionUtil;

public class NodeMapReduceTaskImpl<KeyIn, ValueIn, KeyOut, ValueOut> extends AbstractMapReduceTask<KeyIn, ValueIn, KeyOut, ValueOut> {

    private final NodeEngine nodeEngine;

    public NodeMapReduceTaskImpl(String name, NodeEngine nodeEngine) {
        super(name);
        this.nodeEngine = nodeEngine;
    }

    @Override
    protected Map<Integer, Object> invokeTasks() throws Exception {
        OperationService os = nodeEngine.getOperationService();
        MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut> operation;
        operation = new MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut>(name, mapper, reducer);
        operation.setNodeEngine(nodeEngine).setCallerUuid(nodeEngine.getLocalMember().getUuid());
        return os.invokeOnAllPartitions(MapService.SERVICE_NAME, new BinaryOperationFactory(operation, nodeEngine));
    }

    @Override
    protected <R> MapReduceBackgroundTask<R> buildMapReduceBackgroundTask(MapReduceListener<KeyOut, ValueOut> listener) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected <R> MapReduceBackgroundTask<R> buildMapReduceBackgroundTask(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> collatorListener) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected <R> void invokeAsyncTask(MapReduceBackgroundTask<R> task) {
        ExecutorService es = nodeEngine.getExecutionService().getExecutor("hz:query");
        es.execute(task);
    }

    private class NodeMapReduceBackgroundTask<R> extends MapReduceBackgroundTask<R> {

        private NodeMapReduceBackgroundTask(MapReduceListener<KeyOut, ValueOut> listener) {
            super(listener);
        }

        private NodeMapReduceBackgroundTask(Collator<KeyOut, ValueOut, R> collator, MapReduceCollatorListener<R> collatorListener) {
            super(collator, collatorListener);
        }

        @Override
        public void run() {
            OperationService os = nodeEngine.getOperationService();
            MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut> operation;
            operation = new MapReduceOperation<KeyIn, ValueIn, KeyOut, ValueOut>(name, mapper, reducer);
            operation.setNodeEngine(nodeEngine).setCallerUuid(nodeEngine.getLocalMember().getUuid());
            try {
                Map<Integer, Object> responses = os.invokeOnAllPartitions(MapService.SERVICE_NAME, new BinaryOperationFactory(operation, nodeEngine));
                Map groupedResponses = groupResponsesByKey(responses);
                Map reducedResults;
                if (reducer != null) {
                    reducedResults = finalReduceStep(groupedResponses);
                } else {
                    reducedResults = groupedResponses;
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
