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

package com.hazelcast.config;

public class MapIndexConfig {

    private String attribute;

    private boolean ordered = false;

    public MapIndexConfig() {
        super();
    }

    public MapIndexConfig(String attribute, boolean ordered) {
        super();
        this.attribute = attribute;
        this.ordered = ordered;
    }

    public String getAttribute() {
        return attribute;
    }

    public MapIndexConfig setAttribute(String attribute) {
        this.attribute = attribute;
        return this;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public MapIndexConfig setOrdered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MapIndexConfig{");
        sb.append("attribute='").append(attribute).append('\'');
        sb.append(", ordered=").append(ordered);
        sb.append('}');
        return sb.toString();
    }
}
