package com.hazelcast.config;
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

import com.hazelcast.map.MapService;

import java.util.ArrayList;
import java.util.List;

public class ReplicatedMapConfig {

    public static final int DEFAULT_CONSISTENCY_LEVEL = 1;

    private String name = MapService.REPLICATED_MAP_BASE_NAME + "default";

    private DistributionStrategyConfig distributionStrategyConfig = DistributionStrategyConfig.Distributed;

    private int timeToLiveSeconds = MapConfig.DEFAULT_TTL_SECONDS;

    private InMemoryFormat inMemoryFormat = InMemoryFormat.BINARY;

    private WanReplicationRef wanReplicationRef = null;

    private boolean statisticsEnabled = true;

    private int consistencyLevel = DEFAULT_CONSISTENCY_LEVEL;

    private List<MapIndexConfig> mapIndexConfigs = null;

    private List<EntryListenerConfig> listenerConfigs = null;

    private PartitioningStrategyConfig partitioningStrategyConfig = null;

    private String mergePolicy = MapConfig.DEFAULT_MAP_MERGE_POLICY;

    private boolean optimizeQueries = false;

    public ReplicatedMapConfig() {
    }

    public ReplicatedMapConfig(ReplicatedMapConfig replicatedMapConfig) {
        this.name = replicatedMapConfig.name;
    }

    public String getName() {
        return name;
    }

    public ReplicatedMapConfig setName(String name) {
        this.name = name;
        return this;
    }

    public DistributionStrategyConfig getDistributionStrategyConfig() {
        return distributionStrategyConfig;
    }

    public ReplicatedMapConfig setDistributionStrategyConfig(DistributionStrategyConfig distributionStrategyConfig) {
        this.distributionStrategyConfig = distributionStrategyConfig;
        return this;
    }

    public int getConsistencyLevel() {
        return consistencyLevel;
    }

    public ReplicatedMapConfig setConsistencyLevel(int consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
        return this;
    }

    public InMemoryFormat getInMemoryFormat() {
        return inMemoryFormat;
    }

    public ReplicatedMapConfig setInMemoryFormat(InMemoryFormat inMemoryFormat) {
        this.inMemoryFormat = inMemoryFormat;
        return this;
    }

    /**
     * @return the timeToLiveSeconds
     */
    public int getTimeToLiveSeconds() {
        return timeToLiveSeconds;
    }

    /**
     * Maximum number of seconds for each entry to stay in the map. Entries that are
     * older than timeToLiveSeconds will get automatically evicted from the map.
     * Updates on the entry don't change the eviction time.
     * Any integer between 0 and Integer.MAX_VALUE.
     * 0 means infinite. Default is 0.
     *
     * @param timeToLiveSeconds the timeToLiveSeconds to set
     */
    public ReplicatedMapConfig setTimeToLiveSeconds(int timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
        return this;
    }

    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public ReplicatedMapConfig setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
        return this;
    }

    public WanReplicationRef getWanReplicationRef() {
        return wanReplicationRef;
    }

    public ReplicatedMapConfig setWanReplicationRef(WanReplicationRef wanReplicationRef) {
        this.wanReplicationRef = wanReplicationRef;
        return this;
    }

    public ReplicatedMapConfig addMapIndexConfig(MapIndexConfig mapIndexConfig) {
        getMapIndexConfigs().add(mapIndexConfig);
        return this;
    }

    public List<MapIndexConfig> getMapIndexConfigs() {
        if (mapIndexConfigs == null) {
            mapIndexConfigs = new ArrayList<MapIndexConfig>();
        }
        return mapIndexConfigs;
    }

    public ReplicatedMapConfig setMapIndexConfigs(List<MapIndexConfig> mapIndexConfigs) {
        this.mapIndexConfigs = mapIndexConfigs;
        return this;
    }

    public PartitioningStrategyConfig getPartitioningStrategyConfig() {
        return partitioningStrategyConfig;
    }

    public ReplicatedMapConfig setPartitioningStrategyConfig(PartitioningStrategyConfig partitioningStrategyConfig) {
        this.partitioningStrategyConfig = partitioningStrategyConfig;
        return this;
    }

    public ReplicatedMapConfig addEntryListenerConfig(EntryListenerConfig listenerConfig) {
        getEntryListenerConfigs().add(listenerConfig);
        return this;
    }

    public List<EntryListenerConfig> getEntryListenerConfigs() {
        if (listenerConfigs == null) {
            listenerConfigs = new ArrayList<EntryListenerConfig>();
        }
        return listenerConfigs;
    }

    public ReplicatedMapConfig setEntryListenerConfigs(List<EntryListenerConfig> listenerConfigs) {
        this.listenerConfigs = listenerConfigs;
        return this;
    }

    public String getMergePolicy() {
        return mergePolicy;
    }

    public ReplicatedMapConfig setMergePolicy(String mergePolicy) {
        this.mergePolicy = mergePolicy;
        return this;
    }
    public boolean isOptimizeQueries() {
        return optimizeQueries;
    }

    public ReplicatedMapConfig setOptimizeQueries(boolean optimizeQueries) {
        this.optimizeQueries = optimizeQueries;
        return this;
    }


}
