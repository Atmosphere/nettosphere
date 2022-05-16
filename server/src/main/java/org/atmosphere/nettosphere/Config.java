/*
 * Copyright 2008-2022 Async-IO.org
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

import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.ssl.SslContext;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.handler.AbstractReflectorAtmosphereHandler;
import org.atmosphere.handler.ReflectorServletProcessor;
import org.atmosphere.nettosphere.util.SSLContextListener;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProtocol;
import org.atmosphere.websocket.protocol.SimpleHttpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * A Configuration class used to configure Atmosphere.
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private final Builder b;

    public Config(Builder b) {
        this.b = b;
    }

    public ExecutorService bossExecutor() {
        return b.bossExecutor;
    }

    public ExecutorService workerExecutor() {
        return b.workerExecutor;
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

    public Class<? extends Broadcaster> broadcaster() {
        return b.broadcasterClass;
    }

    public Map<String, AtmosphereHandler> handlersMap() {
        return b.handlers;
    }

    public Map<String, WebSocketHandler> webSocketHandlersMap() {
        return b.webSocketHandlers;
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

    public SSLContext sslContext() {
        return b.context;
    }
    
    public String[] enabledCipherSuites() {
        return b.enabledCipherSuites;
    }

    public SslContext nettySslContext() {
        return b.sslContext;
    }

    public SSLContextListener sslContextListener() {
        return b.contextListener;
    }

    public LinkedList<ChannelInboundHandler> channelUpstreamHandlers() {
        return b.nettyHandlers;
    }

    public boolean supportChunking() {
        return b.supportChunking;
    }

    public boolean aggregateRequestBodyInMemory() {
        return b.aggregateRequestBodyInMemory;
    }

    public boolean socketKeepAlive() {
        return b.socketKeepAlive;
    }

    public boolean socketNoTcpDelay() {
        return b.socketNoTcpDelay;
    }

    public int maxChunkContentLength() {
        return b.maxContentLength;
    }

    public List<String> excludedInterceptors() {
        return b.excludedInterceptors;
    }

    public boolean enablePong() {
        return b.enablePong;
    }

    public int maxWebSocketFrameSize() {
        return b.maxWebSocketFrameSize;
    }

    public int maxWebSocketFrameAggregatorContentLength() {
        return b.maxWebSocketFrameAggregatorContentLength;
    }

    public boolean textFrameAsBinary() {
        return b.textFrameAsBinary;
    }

    public Map<String, Object> servletContextAttributes() {
        return b.servletContextAttributes;
    }

    public String subProtocols() {
        return b.subProtocols;
    }

    public boolean noInternalAlloc() {
        return b.noInternalAlloc;
    }

    public boolean binaryWrite() {
        return b.binaryWrite;
    }

    public boolean webSocketOnly() {
        return b.webSocketOnly;
    }

    public boolean webSocketCompression() {
        return b.webSocketCompression;
    }

    public boolean forceResponseWriteCompatibility() {
        return b.forceResponseWriteCompatibility;
    }

    public boolean epoll() {
        return b.epoll;
    }
    public IOExceptionHandler ioExceptionHandler() { return b.ioExceptionHandler;}

    public final static class Builder {
        private final List<String> paths = new ArrayList<String>();
        private String atmosphereDotXmlPath = AtmosphereFramework.DEFAULT_ATMOSPHERE_CONFIG_PATH;
        private ExecutorService bossExecutor;
        private ExecutorService workerExecutor;
        private String host = "0.0.0.0";
        private int port = 8080;
        private final Map<String, String> initParams = new HashMap<String, String>();
        private final Map<String, Object> servletContextAttributes = new HashMap<String, Object>();

        private final Map<String, AtmosphereHandler> handlers = new HashMap<String, AtmosphereHandler>();
        private final Map<String, WebSocketHandler> webSocketHandlers = new HashMap<String, WebSocketHandler>();

        private Class<? extends WebSocketProtocol> webSocketProtocol = SimpleHttpProtocol.class;

        private Class<? extends Broadcaster> broadcasterClass;
        private BroadcasterFactory broadcasterFactory;
        private Class<? extends BroadcasterCache> broadcasterCache;
        private final List<AtmosphereInterceptor> interceptors = new ArrayList<AtmosphereInterceptor>();
        private final List<String> excludedInterceptors = new ArrayList<String>();
        private String librariesPath = "." + File.separator + "lib";
        private String mappingPath = "";
        private final List<Class<?>> packages = new ArrayList<Class<?>>();
        private SSLContext context;
        private String[] enabledCipherSuites;
        private SslContext sslContext;
        private SSLContextListener contextListener;
        private final LinkedList<ChannelInboundHandler> nettyHandlers = new LinkedList<ChannelInboundHandler>();
        private boolean supportChunking = true;
        private boolean aggregateRequestBodyInMemory = true;
        private boolean socketNoTcpDelay = true;
        private boolean socketKeepAlive = true;
        private int maxContentLength = 65536;
        private boolean enablePong = false;
        private int maxWebSocketFrameSize = 65536;
        private int maxWebSocketFrameAggregatorContentLength = 65536;
        private boolean textFrameAsBinary = false;
        private String subProtocols = "";
        private boolean noInternalAlloc = false;
        private boolean binaryWrite = false;
        private boolean epoll = false;
        private boolean webSocketOnly;
        private boolean webSocketCompression;
        private boolean forceResponseWriteCompatibility;
        private boolean shareHeaders;
        private IOExceptionHandler ioExceptionHandler = (ctx, e) -> true;

        /**
         * Share underlying headers map with newly created WebSocket. Best used when {@link #noInternalAlloc} is set to true.
         * Default is false
         * @param shareHeaders
         * @return
         */
        public Builder shareHeaders(boolean  shareHeaders) {
            this.shareHeaders = shareHeaders;
            return this;
        }

        /**
         * Set an SSLContext in order enable SSL
         *
         * @param context
         * @return this
         */
        public Builder sslContext(SSLContext context) {
            this.context = context;
            return this;
        }
       
        /**
         * Set a String[] of cipher suites to be enabled.
         *
         * @param String-array of cipher suites to be enabled.
         * @return this
         */
        public Builder enabledCipherSuites(String[] cipherSuitesToEnable) {
            this.enabledCipherSuites = cipherSuitesToEnable;
            return this;
        }

        /**
         * Set the {@link SslContext}
         *
         * @param sslContext
         * @return this
         */
        public Builder sslContext(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Set the maximum WebSocket Frame Size. Default is 65536
         *
         * @param maxWebSocketFrameSize the maximum WebSocket Frame Size.
         * @return this
         */
        public Builder maxWebSocketFrameSize(int maxWebSocketFrameSize) {
            this.maxWebSocketFrameSize = maxWebSocketFrameSize;
            return this;
        }

        /**
         * Set the maximum WebSocket Frame Aggregator Content Size. Default is 65536
         *
         * @param maxWebSocketFrameAggregatorContentLength the maximum WebSocket Frame Aggregator Content Size.
         * @return this
         */
        public Builder maxWebSocketFrameAggregatorContentLength(int maxWebSocketFrameAggregatorContentLength) {
            this.maxWebSocketFrameAggregatorContentLength = maxWebSocketFrameAggregatorContentLength;
            return this;
        }

        /**
         * Add a {@link SSLContextListener}
         *
         * @param listener
         * @return this
         */
        public Builder sslContextListener(SSLContextListener listener) {
            this.contextListener = listener;
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
         * Enable WebSokcet Pong message. Disabled by default.
         *
         * @param enablePong Enable WebSokcet Pong message
         * @return this
         */
        public Builder enablePong(boolean enablePong) {
            this.enablePong = enablePong;
            return this;
        }

        /**
         * When {@link #aggregateRequestBodyInMemory} is true,the maximum length of the aggregated content.
         * If the length of the aggregated content exceeds this value,
         * a {@link TooLongFrameException} will be raised.
         *
         * @return this
         */
        public Builder maxChunkContentLength(int maxChunkContentLength) {
            this.maxContentLength = maxChunkContentLength;
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
         * The Executor to be used in providing (the) I/O boss-thread(s).
         *
         * @param bossExecutor ExecutorService to be used for boss threads.
         * @return this
         */
        public Builder bossExecutor(ExecutorService bossExecutor) {
            this.bossExecutor = bossExecutor;
            return this;
        }

        /**
         * The Executor to be used in providing (the) I/O worker-thread(s).
         *
         * @param workerExecutor ExecutorService to be used for worker threads.
         * @return this
         */
        public Builder workerExecutor(ExecutorService workerExecutor) {
            this.workerExecutor = workerExecutor;
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
         * Add an {@link WebSocketHandler} that will be mapped to the specified path
         *
         * @param path a mapping path
         * @param c    an {@link AtmosphereHandler}
         * @return this
         */
        public Builder resource(String path, WebSocketHandler c) {
            webSocketHandlers.put(path, c);
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
                } else if (WebSocketHandler.class.isAssignableFrom(c)) {
                    webSocketHandlers.put(path, WebSocketHandler.class.cast(c.newInstance()));
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
        public Builder broadcaster(Class<? extends Broadcaster> broadcasterClass) {
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
         * @return this
         */
        public Builder interceptor(AtmosphereInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * Exclude an {@link AtmosphereInterceptor} from being added, at startup, by Atmosphere. The default's {@link AtmosphereInterceptor}
         * are candidates for being excluded
         *
         * @param interceptor an {@link AtmosphereInterceptor}
         * @return this
         */
        public Builder excludeInterceptor(String interceptor) {
            excludedInterceptors.add(interceptor);
            return this;
        }

        /**
         * Add a {@link ChannelInboundHandler}. All will be executed before {@link BridgeRuntime}
         *
         * @param h {@link ChannelInboundHandler}
         * @return this;
         */
        public Builder channelUpstreamHandler(ChannelInboundHandler h) {
            nettyHandlers.addLast(h);
            return this;
        }

        /**
         * Set to false to override the default behavior when writing bytes, which is use chunking. When set to false
         * the {@link ChunkedWriteHandler} will not be added to the Netty's {@link ChannelPipeline}
         * <p/>
         * This is strongly recommended to turn chunking to false if you are using websocket to get better performance.
         *
         * @param supportChunking false to disable.
         * @return this
         */
        public Builder supportChunking(boolean supportChunking) {
            this.supportChunking = supportChunking;
            return this;
        }

        /**
         * By default, Nettosphere aggregate the HTTP request's body in memory an invoke an Atmosphere's components with
         * a single {@link AtmosphereResource}. Setting supportChunkAggregator to false will instead invoke Atmosphere's component
         * with a new {@link AtmosphereResource} each time the request's body is read in memory. Setting to false
         * may significantly increase the performance and reduce memory footprint. Note that setting this value to false
         * may deliver to your Atmosphere's component partial body, so your application must make sure to aggregate the
         * body before parsing the data if needed. For example, if you are using JSON as format, make sure you parse the
         * data incrementally.
         *
         * @param aggregateRequestBodyInMemory false to disable.
         * @return this
         */
        public Builder aggregateRequestBodyInMemory(boolean aggregateRequestBodyInMemory) {
            this.aggregateRequestBodyInMemory = aggregateRequestBodyInMemory;
            return this;
        }

        /**
         * Set Netty's Bootstrap 'child.tcpDelay'
         *
         * @param socketNoTcpDelay
         * @return this
         */
        public Builder socketNoTcpDelay(boolean socketNoTcpDelay) {
            this.socketNoTcpDelay = socketNoTcpDelay;
            return this;
        }

        /**
         * Set Netty's Bootstrap 'child.keepAlive'
         *
         * @param socketKeepAlive
         * @return this
         */
        public Builder socketKeepAlive(boolean socketKeepAlive) {
            this.socketKeepAlive = socketKeepAlive;
            return this;
        }

        /**
         * Do not decode {@link TextWebSocketFrame} into a String and instead pass
         * it to the {@link org.atmosphere.websocket.WebSocketProcessor} as binary.
         *
         * @param textFrameAsBinary
         * @return this
         */
        public Builder textFrameAsBinary(boolean textFrameAsBinary) {
            this.textFrameAsBinary = textFrameAsBinary;
            return this;
        }

        /**
         * A coma delimited of allowed WebSocket Sub Protocol (Sec-WebSocket-Protocol)
         *
         * @param subProtocols A coma delimited of allowed WebSocket Sub Protocol
         * @return this
         */
        public Builder subProtocols(String subProtocols) {
            this.subProtocols = subProtocols;
            return this;
        }

        /**
         * Proxy {@link org.atmosphere.cpr.AtmosphereRequest}, {@link AtmosphereResource} and {@link org.atmosphere.cpr.AtmosphereRequest}
         * with no ops implementations.
         * <p/>
         * Set it to true only if you are using WebSocket with a your own implementation of {@link org.atmosphere.websocket.WebSocketProcessor}. The WebSocketProcessor MUST not invoked those objects and only use the {@link org.atmosphere.websocket.WebSocket} API.
         * <p/>
         * Default is false
         *
         * @param noInternalAlloc
         * @return this
         */
        public Builder noInternalAlloc(boolean noInternalAlloc) {
            this.noInternalAlloc = noInternalAlloc;
            return this;
        }

        /**
         * Write binary frame when websocket transport is used.
         *
         * @param binaryWrite true or false
         * @return this
         */
        public Builder binaryWrite(boolean binaryWrite) {
            this.binaryWrite = binaryWrite;
            return this;
        }

        /**
         * Only Support WebSocket. All HTTP call will be dropped. Default is false
         * @param webSocketOnly true if only the websocket protocol needs to be suppored.
         * @return this
         */
        public Builder webSocketOnly(boolean webSocketOnly) {
            this.webSocketOnly = webSocketOnly;
            return this;
        }

        /**
         * true if webSocket compression needs to be used. Default is false
         * @param webSocketCompression true if webSocket compression needs to be used
         * @return this
         */
        public Builder webSocketCompression(boolean webSocketCompression) {
            this.webSocketCompression = webSocketCompression;
            return this;
        }

        /**
         * true if Jersey or an external server that manipulate the response
         * @param forceResponseWriteCompatibility true if Jersey or an external server that manipulate the response
         * @return this
         */
        public Builder forceResponseWriteCompatibility(boolean forceResponseWriteCompatibility) {
            this.forceResponseWriteCompatibility = forceResponseWriteCompatibility;
            return this;
        }

        /**
         * Set an {@link IOExceptionHandler} to handle unexpected I/O exception
         * @param ioExceptionHandler
         * @return this
         */
        public Builder ioExceptionHandler(IOExceptionHandler ioExceptionHandler) {
            this.ioExceptionHandler = ioExceptionHandler;
            return this;
        }

        /**
         * Build an instance of this class.
         *
         * @return this;
         */
        public Config build() {
            if (paths.isEmpty()) {
                paths.add("/");
            }
            if (context != null) {
                if (enabledCipherSuites == null) {
                    throw new IllegalStateException("Incomplete Config: SSLContext requires cipherSuites to be specified.");
                }
                if (contextListener == null) {
                    contextListener = new SSLContextListener() {
                        
                        @Override
                        public void onPostCreate(SSLEngine e) {
                            e.setEnabledCipherSuites(enabledCipherSuites);
                            e.setUseClientMode(false);     
                        }
                    };
                }
            }
            return new Config(this);
        }

        /**
         * Set ServletContext Attribute
         *
         * @param name
         * @param value
         * @return this
         */
        public Builder servletContextAttribute(String name, Object value) {
            servletContextAttributes.put(name, value);
            return this;
        }

        /**
         * Use {@link io.netty.channel.epoll.EpollEventLoopGroup}
         * @See http://netty.io/wiki/native-transports.html
         *
         * @return this
         */
        public Builder epoll(boolean epoll) {
            this.epoll = epoll;
            return this;
        }
    }
}
