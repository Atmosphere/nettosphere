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

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.handler.AbstractReflectorAtmosphereHandler;
import org.atmosphere.handler.ReflectorServletProcessor;
import org.atmosphere.websocket.WebSocketProtocol;
import org.atmosphere.websocket.protocol.SimpleHttpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public List<String> path() {
        return b.paths;
    }

    public String configFile() {
        return b.atmosphereDotXmlPath;
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

    public List<AtmosphereInterceptor> interceptors() {
        return b.interceptors;
    }

    public String scanLib() {
        return b.librariesPath;
    }

    public List<Class<?>> scanPackages() {
        return b.packages;
    }

    public String mappingPath() {
        return b.mappingPath;
    }

    public SSLEngine engine() {
        return b.engine;
    }

    public final static class Builder {
        private List<String> paths = new ArrayList<String>();
        private String atmosphereDotXmlPath = AtmosphereFramework.DEFAULT_ATMOSPHERE_CONFIG_PATH;
        private String host = "localhost";
        private int port = 8080;
        private final Map<String, String> initParams = new HashMap<String, String>();
        private final Map<String, AtmosphereHandler> handlers = new HashMap<String, AtmosphereHandler>();
        private Class<? extends WebSocketProtocol> webSocketProtocol = SimpleHttpProtocol.class;

        private Class<Broadcaster> broadcasterClass;
        private BroadcasterFactory broadcasterFactory;
        private Class<? extends BroadcasterCache> broadcasterCache;
        private final List<AtmosphereInterceptor> interceptors = new ArrayList<AtmosphereInterceptor>();
        private String librariesPath = "." + File.separator + "lib";
        private String mappingPath = "";
        private final List<Class<?>> packages = new ArrayList<Class<?>>();
        private SSLEngine engine;

        /**
         * Set an SSLEngine in order enable SSL
         * @param engine
         * @return this
         */
        public Builder engine(SSLEngine engine) {
            this.engine = engine;
            return this;
        }

        /**
         * Set the mapping path. If you have worked with Servlet, the mapping path is equivalent to the servlet path.
         *
         * @param mappingPath the path under which the application will be mapped.
         * @return this
         */
        public Builder mappingPath(String mappingPath) {
            this.mappingPath = mappingPath;
            return this;
        }

        /**
         * Set the path to the library when annotation scanning is enabled. Default is "./". Use this method
         * when your annotated resource is packaged inside the jar/zip.
         *
         * @param librariesPath the path to the library when annotation scanning is enabled.
         * @return this
         */
        public Builder scanLibrary(String librariesPath) {
            this.librariesPath = librariesPath;
            return this;
        }

        /**
         * The path location of the atmosphere.xml file.
         *
         * @param atmosphereDotXmlPath path location of the atmosphere.xml file.
         * @return this
         */
        public Builder configFile(String atmosphereDotXmlPath) {
            this.atmosphereDotXmlPath = atmosphereDotXmlPath;
            return this;
        }

        /**
         * The server's host
         *
         * @param host server's host
         * @return this
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * The server's port
         *
         * @param port server's port
         * @return this
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Add some init param
         *
         * @param name  the name
         * @param value the value
         * @return this
         */
        public Builder initParam(String name, String value) {
            initParams.put(name, value);
            return this;
        }

        /**
         * Add a path to scan when looking for static resources like javascript file, html, etc.
         *
         * @param path
         * @return this
         */
        public Builder resource(String path) {
            paths.add(path);
            return this;
        }

        /**
         * Add an {@link AtmosphereHandler} that will be mapped to the specified path
         *
         * @param path a mapping path
         * @param c    an {@link AtmosphereHandler}
         * @return this
         */
        public Builder resource(String path, AtmosphereHandler c) {
            handlers.put(path, c);
            return this;
        }

        /**
         * Add an {@link Servlet} that will be mapped to the specified path
         *
         * @param path a mapping path
         * @param c    an {@link Servlet}
         * @return this
         */
        public Builder resource(String path, Servlet c) {
            handlers.put(path, new ReflectorServletProcessor(c));
            return this;
        }

        /**
         * Add an {@link Handler} mapped to the default, which is '/*'
         *
         * @param handler {@link Handler}
         * @return this
         */
        public Builder resource(Handler handler) {
            return resource("/*", handler);
        }

        /**
         * Add an {@link Handler} that will be mapped to the specified path
         *
         * @param handler {@link Handler}
         * @return this
         */
        public Builder resource(String path, final Handler handler) {
            handlers.put(path, new AbstractReflectorAtmosphereHandler() {
                @Override
                public void onRequest(AtmosphereResource resource) throws IOException {
                    handler.handle(resource);
                }

                @Override
                public void destroy() {
                }
            });
            return this;
        }

        /**
         * Add an annotated class. The annotation can be from Atmosphere or Jersey.
         *
         * @param c an annotated class. The annotation can be from Atmosphere or Jersey.
         * @return this
         */
        public Builder resource(Class<?> c) {
            packages.add(c);
            return this;
        }

        /**
         * Add an {@link AtmosphereHandler} or {@link Servlet} class
         *
         * @param path a mapping path
         * @param c    an {@link AtmosphereHandler} or {@link Servlet} class
         * @return this
         */
        public Builder resource(String path, Class<?> c) {
            try {
                if (AtmosphereHandler.class.isAssignableFrom(c)) {
                    handlers.put(path, AtmosphereHandler.class.cast(c.newInstance()));
                } else if (Servlet.class.isAssignableFrom(c)) {
                    handlers.put(path, new ReflectorServletProcessor(Servlet.class.cast(c.newInstance())));
                } else {
                    throw new IllegalStateException("You class must implements AtmosphereHandler or be a Servlet");
                }
            } catch (Exception ex) {
                logger.error("Invalid resource {}", c);
            }
            return this;
        }

        /**
         * Configure the default {@link Broadcaster}
         *
         * @param broadcasterClass a Broadcaster
         * @return this
         */
        public Builder broadcaster(Class<Broadcaster> broadcasterClass) {
            this.broadcasterClass = broadcasterClass;
            return this;
        }

        /**
         * Configure the default {@link BroadcasterFactory}
         *
         * @param broadcasterFactory a BroadcasterFactory's class
         * @return this
         */
        public Builder broadcasterFactory(BroadcasterFactory broadcasterFactory) {
            this.broadcasterFactory = broadcasterFactory;
            return this;
        }

        /**
         * Configure the default {@link BroadcasterCache}
         *
         * @param broadcasterCache a BroadcasterCache's class
         * @return this
         */
        public Builder broadcasterCache(Class<? extends BroadcasterCache> broadcasterCache) {
            this.broadcasterCache = broadcasterCache;
            return this;
        }

        /**
         * Configure the default {@link WebSocketProtocol}
         *
         * @param webSocketProtocol a WebSocketProtocol's class
         * @return this
         */
        public Builder webSocketProtocol(Class<? extends WebSocketProtocol> webSocketProtocol) {
            this.webSocketProtocol = webSocketProtocol;
            return this;
        }

        /**
         * Add an {@link AtmosphereInterceptor}
         *
         * @param interceptor an {@link AtmosphereInterceptor}
         * @return
         */
        public Builder interceptor(AtmosphereInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * Build an instance of this class.
         *
         * @return
         */
        public Config build() {
            if (paths.isEmpty()) {
                paths.add("/");
            }
            return new Config(this);
        }
    }
}
