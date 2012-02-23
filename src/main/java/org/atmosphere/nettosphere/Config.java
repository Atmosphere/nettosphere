/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.nettosphere;

import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A Configuration class used to configure Atmosphere.
 */
public class Config {

    private final Builder b;

    public Config(Builder b) {
        this.b = b;
    }

    public String host() {
        return b.host;
    }

    public int port() {
        return b.port;
    }

    public Map<String, String> initParams() {
        return b.initParams;
    }

    public String path() {
        return b.applicationPath;
    }

    public Class<Broadcaster> broadcaster() {
        return b.broadcasterClass;
    }

    public Map<String, AtmosphereHandler<?, ?>> handlersMap() {
        return b.handlers;
    }

    public Map<String, Class<? extends AtmosphereHandler<?, ?>>> classHandlerMap() {
        return b.classHandlers;
    }

    public BroadcasterFactory broadcasterFactory() {
        return b.broadcasterFactory;
    }

    public Class<? extends BroadcasterCache<?, ?>> broadcasterCache() {
        return b.broadcasterCache;
    }

    public final static class Builder {
        private String applicationPath = "/";
        private String host = "localhost";
        private int port = 8080;
        private final Map<String, String> initParams = new HashMap<String, String>();
        private final Map<String, AtmosphereHandler<?, ?>> handlers = new HashMap<String, AtmosphereHandler<?, ?>>();
        private final Map<String, Class<? extends AtmosphereHandler<?, ?>>> classHandlers = new HashMap<String, Class<? extends AtmosphereHandler<?, ?>>>();
        private Class<Broadcaster> broadcasterClass;
        private BroadcasterFactory broadcasterFactory;
        private Class<? extends BroadcasterCache<?, ?>> broadcasterCache;

        public Builder path(String applicationPath) {
            this.applicationPath = applicationPath;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder initParam(String name, String value) {
            initParams.put(name, value);
            return this;
        }

        public Builder handler(String path, AtmosphereHandler<?, ?> c) {
            handlers.put(path, c);
            return this;
        }

        public Builder handler(String path, Class<? extends AtmosphereHandler<?, ?>> c) {
            classHandlers.put(path, c);
            return this;
        }

        public Builder broadcaster(Class<Broadcaster> broadcasterClass) {
            this.broadcasterClass = broadcasterClass;
            return this;
        }

        public Builder broadcasterFactory(BroadcasterFactory broadcasterFactory) {
            this.broadcasterFactory = broadcasterFactory;
            return this;
        }

        public Builder broadcasterCache(Class<? extends BroadcasterCache<?, ?>> broadcasterCache) {
            this.broadcasterCache = broadcasterCache;
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }
}
