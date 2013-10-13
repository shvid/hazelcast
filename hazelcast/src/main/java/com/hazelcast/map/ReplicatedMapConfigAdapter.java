package com.hazelcast.map;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.hazelcast.config.DistributionStrategyConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.ReplicatedMapConfig;

public class ReplicatedMapConfigAdapter extends MapConfig {

    private final String baseName;

    private final DistributionStrategyConfig distributionStrategyConfig;

    private final int consistencyLevel;

    ReplicatedMapConfigAdapter(ReplicatedMapConfig replicatedMapConfig) {
        super.setAsyncBackupCount(0);
        super.setMaxIdleSeconds(0);
        super.setEvictionPolicy(EvictionPolicy.NONE);
        super.setReadBackupData(true);

        super.setName(replicatedMapConfig.getName());
        super.setTimeToLiveSeconds(replicatedMapConfig.getTimeToLiveSeconds());
        super.setBackupCount(replicatedMapConfig.getConsistencyLevel());
        super.setEntryListenerConfigs(replicatedMapConfig.getEntryListenerConfigs());
        super.setMapIndexConfigs(replicatedMapConfig.getMapIndexConfigs());
        super.setInMemoryFormat(replicatedMapConfig.getInMemoryFormat());
        super.setMergePolicy(replicatedMapConfig.getMergePolicy());
        super.setPartitioningStrategyConfig(replicatedMapConfig.getPartitioningStrategyConfig());
        super.setOptimizeQueries(replicatedMapConfig.isOptimizeQueries());
        super.setStatisticsEnabled(replicatedMapConfig.isStatisticsEnabled());
        super.setWanReplicationRef(replicatedMapConfig.getWanReplicationRef());

        this.distributionStrategyConfig = replicatedMapConfig.getDistributionStrategyConfig();
        this.baseName = replicatedMapConfig.getName().replace(MapService.REPLICATED_MAP_BASE_NAME, "");
        this.consistencyLevel = replicatedMapConfig.getConsistencyLevel();
    }

    public DistributionStrategyConfig getDistributionStrategyConfig() {
        return distributionStrategyConfig;
    }

    public String getBaseName() {
        return baseName;
    }

    public int getConsistencyLevel() {
        return consistencyLevel;
    }

}
