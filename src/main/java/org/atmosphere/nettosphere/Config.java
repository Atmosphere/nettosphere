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
import org.atmosphere.handler.ReflectorServletProcessor;
import org.atmosphere.websocket.WebSocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.util.HashMap;
import java.util.Map;

/**
 * A Configuration class used to configure Atmosphere.
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
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
        return b.staticResourcePath;
    }

    public Class<Broadcaster> broadcaster() {
        return b.broadcasterClass;
    }

    public Map<String, AtmosphereHandler> handlersMap() {
        return b.handlers;
    }

    public BroadcasterFactory broadcasterFactory() {
        return b.broadcasterFactory;
    }

    public Class<? extends BroadcasterCache> broadcasterCache() {
        return b.broadcasterCache;
    }

    public Class<? extends WebSocketProtocol> webSocketProtocol() {
        return b.webSocketProtocol;
    }

    public final static class Builder {
        private String staticResourcePath = "/";
        private String host = "localhost";
        private int port = 8080;
        private final Map<String, String> initParams = new HashMap<String, String>();
        private final Map<String, AtmosphereHandler> handlers = new HashMap<String, AtmosphereHandler>();
        private Class<? extends WebSocketProtocol> webSocketProtocol;

        private Class<Broadcaster> broadcasterClass;
        private BroadcasterFactory broadcasterFactory;
        private Class<? extends BroadcasterCache> broadcasterCache;

        /**
         * The path location of static resource (e.g html)
         * @param staticResourcePath
         * @return
         */
        public Builder resource(String staticResourcePath) {
            this.staticResourcePath = staticResourcePath;
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

        public Builder resource(String path, AtmosphereHandler c) {
            handlers.put(path, c);
            return this;
        }

        public Builder resource(String path, Servlet c) {
            handlers.put(path, new ReflectorServletProcessor(c));
            return this;
        }

        public Builder resource(Class<?> c)  {
            return resource(staticResourcePath, c);
        }

        public Builder resource(String path, Class<?> c)  {
            try {
                if (AtmosphereHandler.class.isAssignableFrom(c)) {
                    handlers.put(path, AtmosphereHandler.class.cast(c.newInstance()));
                } else if (Servlet.class.isAssignableFrom(c)) {
                    handlers.put(path, new ReflectorServletProcessor(Servlet.class.cast(c.newInstance())));
                } else {
                    // TODO: NOT clear
                    staticResourcePath = path;
                    initParam("com.sun.jersey.config.property.packages", c.getPackage().getName());
                }
            } catch (Exception ex) {
                logger.error("Invalid resource {}", c);
            }
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

        public Builder broadcasterCache(Class<? extends BroadcasterCache> broadcasterCache) {
            this.broadcasterCache = broadcasterCache;
            return this;
        }

        public Builder webSocketProtocol(Class<? extends WebSocketProtocol> webSocketProtocol) {
            this.webSocketProtocol = webSocketProtocol;
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }
}
