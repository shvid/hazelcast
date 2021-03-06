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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ConfigLoader {

    public static Config load(final String path) throws IOException {
        final URL url = locateConfig(path);
        if (url == null) {
            return null;
        }
        return new UrlXmlConfig(url);
    }

    public static URL locateConfig(final String path) {
        URL url = asFile(path);
        if (url == null) {
            url = asURL(path);
        }
        if (url == null) {
            url = asResource(path);
        }
        return url;
    }

    private static URL asFile(final String path) {
        File file = new File(path);
        if (file.exists()) {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException ignored) {
            }
        }
        return null;
    }

    private static URL asURL(final String path) {
        try {
            return new URL(path);
        } catch (MalformedURLException ignored) {
        }
        return null;
    }

    private static URL asResource(final String path) {
        URL url = null;
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            url = contextClassLoader.getResource(path);
        }
        if (url == null) {
            url = ConfigLoader.class.getClassLoader().getResource(path);
        }
        if (url == null) {
            url = ClassLoader.getSystemClassLoader().getResource(path);
        }
        return url;
    }
}
