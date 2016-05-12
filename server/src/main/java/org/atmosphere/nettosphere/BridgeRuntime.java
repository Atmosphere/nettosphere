/*
 * Copyright 2015 Async-IO.org
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

import org.atmosphere.container.NettyCometSupport;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereMappingException;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereRequestImpl;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.nettosphere.util.ChannelBufferPool;
import org.atmosphere.util.FakeHttpSession;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketEventListener;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketPingPongListener;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.cookie.Cookie;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.AtmosphereFramework.REFLECTOR_ATMOSPHEREHANDLER;
import static org.atmosphere.cpr.HeaderConfig.SSE_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;
import static org.atmosphere.websocket.WebSocketEventListener.WebSocketEvent.TYPE.HANDSHAKE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Bridge the Atmosphere Framework with Netty.
 *
 * @author Jeanfrancois Arcand
 */
public class BridgeRuntime extends HttpStaticFileServerHandler {
    private final static String KEEP_ALIVE = BridgeRuntime.class.getName() + "_keep-alive";
    private static final Logger logger = LoggerFactory.getLogger(BridgeRuntime.class);
    private final AtmosphereFramework framework;
    private final Config config;
    private final ScheduledExecutorService suspendTimer;
    private final ConcurrentHashMap<String, HttpSession> sessions = new ConcurrentHashMap<String, HttpSession>();
    private final AtomicBoolean isShutdown = new AtomicBoolean();
    private final WebSocketProcessor webSocketProcessor;
    private final ChannelGroup httpChannels = new DefaultChannelGroup("http");
    private final ChannelGroup websocketChannels = new DefaultChannelGroup("ws");
    private final ChannelBufferPool channelBufferPool;
    private final AsynchronousProcessor asynchronousProcessor;
    private final int maxWebSocketFrameSize;

    private final AtmosphereRequest proxiedRequest;
    private final AtmosphereResponse proxiedResponse;
    private final AtmosphereResource proxiedResource;

    private int webSocketTimeout = -1;

    public BridgeRuntime(final Config config) {
        super(config.path());
        this.config = config;
        framework = new AtmosphereFramework();

        if (config.broadcaster() != null) {
            framework.setDefaultBroadcasterClassName(config.broadcaster().getName());
        }

        framework.setAtmosphereDotXmlPath(config.configFile());

        try {
            if (config.broadcasterFactory() != null) {
                framework.setBroadcasterFactory(config.broadcasterFactory());
            }
        } catch (Throwable t) {
            logger.trace("", t);
        }

        if (config.broadcasterCache() != null) {
            try {
                framework.setBroadcasterCacheClassName(config.broadcasterCache().getName());
            } catch (Throwable t) {
                logger.trace("", t);
            }
        }

        Map<String, AtmosphereHandler> handlersMap = config.handlersMap();
        for (Map.Entry<String, AtmosphereHandler> e : handlersMap.entrySet()) {
            framework.addAtmosphereHandler(e.getKey(), e.getValue());
        }

        final Map<String, WebSocketHandler> webSocketHandlerMap = config.webSocketHandlersMap();

        if (handlersMap.size() == 0 && !webSocketHandlerMap.isEmpty()) {
            framework.addAtmosphereHandler(Broadcaster.ROOT_MASTER, REFLECTOR_ATMOSPHEREHANDLER);
        }

        framework.getAtmosphereConfig().startupHook(new AtmosphereConfig.StartupHook() {
            @Override
            public void started(AtmosphereFramework framework) {
                for (Map.Entry<String, WebSocketHandler> e : webSocketHandlerMap.entrySet()) {
                    framework.addWebSocketHandler(e.getKey(), e.getValue());
                }
            }
        });

        if (config.webSocketProtocol() != null) {
            framework.setWebSocketProtocolClassName(config.webSocketProtocol().getName());
        }

        for (AtmosphereInterceptor i : config.interceptors()) {
            framework.interceptor(i);
        }

        if (!config.scanPackages().isEmpty()) {
            for (Class<?> s : config.scanPackages()) {
                framework.addAnnotationPackage(s);
            }
        }

        final Context context = new Context.Builder().attributes(config.servletContextAttributes()).contextPath(config.mappingPath()).basePath(config.path()).build();
        ServletContext ctx = (ServletContext) Proxy.newProxyInstance(BridgeRuntime.class.getClassLoader(), new Class[]{ServletContext.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        try {
                            Method stub = Context.class.getMethod(method.getName(), method.getParameterTypes());
                            if (stub != null) {
                                return stub.invoke(context, args);
                            } else {
                                logger.trace("Method {} not supported", method.getName());
                                return null;
                            }
                        } catch (NoSuchMethodException ex) {
                            logger.trace("Method {} not supported", method.getName());
                            return null;
                        }
                    }
                });

        try {
            framework.externalizeDestroy(true).init(new NettyServletConfig(config.initParams(), ctx));
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        int max = framework.getAtmosphereConfig().getInitParameter(ApplicationConfig.WEBSOCKET_IDLETIME, -1);
        if (max != -1) {
            webSocketTimeout = max;
        }

        framework.setAsyncSupport(new NettyCometSupport(framework.getAtmosphereConfig()) {
            @Override
            public Action suspended(AtmosphereRequest request, AtmosphereResponse response) throws IOException, ServletException {
                Action a = super.suspended(request, response);
                if (framework.getAtmosphereConfig().isSupportSession()) {
                    AtmosphereResource r = request.resource();
                    HttpSession s = request.getSession(true);
                    if (s != null) {
                        sessions.put(r.uuid(), request.getSession(true));
                    }
                }
                return a;
            }

            @Override
            public String toString() {
                return "NettoSphereAsyncSupport";
            }
        });

        suspendTimer = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
        webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(framework);

        if (config.supportChunking()) {
            channelBufferPool = new ChannelBufferPool(0, config.writeBufferPoolSize(),
                    config.writeBufferPoolCleanupFrequency(), framework.getAtmosphereConfig());
        } else {
            // Dangerous
            channelBufferPool = null;
        }

        for (String s : config.excludedInterceptors()) {
            framework.excludeInterceptor(s);
        }
        asynchronousProcessor = AsynchronousProcessor.class.cast(framework.getAsyncSupport());
        maxWebSocketFrameSize = config.maxWebSocketFrameSize();

        if (config.noInternalAlloc()) {
            proxiedRequest = new AtmosphereRequestImpl.Builder().build();
            proxiedResponse = new AtmosphereResponseImpl.Builder().build();
            proxiedResource = new AtmosphereResourceImpl();
        } else {
            proxiedRequest = null;
            proxiedResponse = null;
            proxiedResource = null;
        }
    }

    public AtmosphereFramework framework() {
        return framework;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        Object msg = messageEvent.getMessage();

        if (isShutdown.get()) {
            ctx.getChannel().close().addListener(ChannelFutureListener.CLOSE);
            return;
        }

        if (msg instanceof HttpRequest) {
            HttpRequest r = HttpRequest.class.cast(msg);
            // Netty fail to decode headers separated by a ','
            List<String> c = r.headers().getAll("Connection");
            String u = r.headers().get("Upgrade");
            boolean webSocket = false;
            if (u != null && u.equalsIgnoreCase("websocket")) {
                webSocket = true;
            }

            if (!webSocket) {
                for (String connection : c) {
                    if (connection != null && connection.toLowerCase().equalsIgnoreCase("upgrade")) {
                        webSocket = true;
                        break;
                    }
                }
            }

            if (webSocket) {
                handleWebSocketHandshake(ctx, messageEvent);
            } else {
                handleHttp(ctx, messageEvent);
            }
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, messageEvent);
        } else if (msg instanceof HttpChunk) {
            handleHttp(ctx, messageEvent);
        }
    }

    private void handleWebSocketHandshake(final ChannelHandlerContext ctx, MessageEvent messageEvent) throws IOException, URISyntaxException {
        final HttpRequest request = (HttpRequest) messageEvent.getMessage();

        // Allow only GET methods.
        if (request.getMethod() != GET) {
            sendHttpResponse(ctx, request, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(request),
                config.subProtocols(),
                false,
                maxWebSocketFrameSize);

        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(request);

        if (handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        } else {

            final AtmosphereRequest atmosphereRequest
                    = createAtmosphereRequest(ctx, request);

            if (!webSocketProcessor.handshake(atmosphereRequest)) {
                sendError(ctx, BAD_REQUEST, null);
                return;
            }

            final NettyWebSocket webSocket = new NettyWebSocket(ctx.getChannel(),
                    framework.getAtmosphereConfig(),
                    config.noInternalAlloc(),
                    config.binaryWrite(),
                    config.shareheaders() ? getHeaders(request) : null);

            webSocketProcessor.notifyListener(webSocket, new WebSocketEventListener.WebSocketEvent("", HANDSHAKE, webSocket));

            if (!config.subProtocols().isEmpty()) {
                if (request.headers().get("Sec-WebSocket-Protocol") == null || request.headers().get("Sec-WebSocket-Protocol").isEmpty()) {
                    request.headers().set("Sec-WebSocket-Protocol", UUID.randomUUID().toString());
                }
            }

            handshaker.handshake(ctx.getChannel(), request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        logger.error("Unable to handshake {}", ctx.getChannel());
                        future.getChannel().close();
                    } else {
                        websocketChannels.add(ctx.getChannel());

                        ctx.getChannel().setAttachment(webSocket);
                        if (config.noInternalAlloc()) {
                            webSocket.resource(proxiedResource);
                        }

                        AtmosphereResponse response = config.noInternalAlloc() ? proxiedResponse :
                                AtmosphereResponseImpl.newInstance(framework.getAtmosphereConfig(), atmosphereRequest, webSocket);
                        webSocketProcessor.open(webSocket, atmosphereRequest, response);

                        if (webSocketTimeout > 0) {
                            webSocket.closeFuture(suspendTimer.scheduleAtFixedRate(new Runnable() {
                                @Override
                                public void run() {
                                    if (webSocket.lastWriteTimeStampInMilliseconds() != 0 && (System.currentTimeMillis() - webSocket.lastWriteTimeStampInMilliseconds() > webSocketTimeout)) {
                                        logger.debug("Timing out {} at {}", webSocket.uuid(), address(ctx.getChannel()));
                                        webSocket.close();
                                    }
                                }
                            }, webSocketTimeout, webSocketTimeout, TimeUnit.MILLISECONDS));
                        }
                    }
                }
            });
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        WebSocketFrame frame = (WebSocketFrame) messageEvent.getMessage();

        // Check for closing frame
        Object attachment = ctx.getChannel().getAttachment();
        if (frame instanceof CloseWebSocketFrame) {
            ctx.getChannel().write(frame).addListener(ChannelFutureListener.CLOSE);
        } else if (frame instanceof PingWebSocketFrame) {
            ChannelBuffer binaryData = frame.getBinaryData();
            if (WebSocketPingPongListener.class.isAssignableFrom(webSocketProcessor.getClass())) {
                WebSocketPingPongListener.class.cast(webSocketProcessor).onPing((WebSocket) attachment, binaryData.array(), binaryData.arrayOffset(), binaryData.readableBytes());
            } else {
                ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
            }
        } else if (frame instanceof BinaryWebSocketFrame || (frame instanceof TextWebSocketFrame && config.textFrameAsBinary())) {
            ChannelBuffer binaryData = frame.getBinaryData();
            webSocketProcessor.invokeWebSocketProtocol((WebSocket) attachment, binaryData.array(), binaryData.arrayOffset(), binaryData.readableBytes());
        } else if (frame instanceof TextWebSocketFrame) {
            webSocketProcessor.invokeWebSocketProtocol((WebSocket) attachment, ((TextWebSocketFrame) frame).getText());
        } else if (frame instanceof PongWebSocketFrame) {
            ChannelBuffer binaryData = frame.getBinaryData();

            if (WebSocketPingPongListener.class.isAssignableFrom(webSocketProcessor.getClass())) {
                WebSocketPingPongListener.class.cast(webSocketProcessor).onPong((WebSocket) attachment, binaryData.array(), binaryData.arrayOffset(), binaryData.readableBytes());
            }

            if (config.enablePong()) {
                ctx.getChannel().write(new PingWebSocketFrame(frame.getBinaryData()));
            } else {
                logger.trace("Received Pong Frame on Channel {}", ctx.getChannel());
            }
        } else {
            logger.warn("{} frame types not supported", frame.getClass());
            ctx.getChannel().close();
        }
    }

    private AtmosphereRequest createAtmosphereRequest(final ChannelHandlerContext ctx, final HttpRequest request) throws URISyntaxException, UnsupportedEncodingException, MalformedURLException {
        if (config.noInternalAlloc()) {
            return proxiedRequest;
        }

        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.getUri());
        final String ct = HttpHeaders.getHeader(request, "Content-Type", "text/plain");
        final long cl = HttpHeaders.getContentLength(request, 0);
        String method = request.getMethod().getName();

        String queryString = requestUri.getQuery();
        Map<String, String[]> qs = new HashMap<String, String[]>();
        if (queryString != null) {
            parseQueryString(qs, queryString);
        }

        if (ct.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            parseQueryString(qs, new String(request.getContent().array(), "UTF-8"));
        }

        String u = requestUri.toURL().toString();
        int last = !u.contains("?") ? u.length() : u.indexOf("?");
        String url = u.substring(0, last);
        int l;

        if (url.contains(config.mappingPath())) {
            l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3 + config.mappingPath().length();
        } else {
            l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3;
        }

        HttpSession session = null;
        if (framework.getAtmosphereConfig().isSupportSession()) {
            String[] transport = qs.get(HeaderConfig.X_ATMOSPHERE_TRANSPORT);
            if (transport != null && transport.length > 0) {
                String[] uuid = qs.get(HeaderConfig.X_ATMOSPHERE_TRACKING_ID);
                if (uuid != null && uuid.length > 0) {
                    // TODO: Session is only supported until an unsubscribe is received.
                    if (transport[0].equalsIgnoreCase(HeaderConfig.DISCONNECT_TRANSPORT_MESSAGE)) {
                        sessions.remove(uuid[0]);
                    } else {
                        session = sessions.get(uuid[0]);

                        if (session == null) {
                            session = new FakeHttpSession("-1", null, System.currentTimeMillis(), -1);
                        }
                    }
                }
            }
        }

        final Map<String, Object> attributes = new HashMap<String, Object>();
        AtmosphereRequestImpl.Builder requestBuilder = new AtmosphereRequestImpl.Builder();
        requestBuilder.requestURI(url.substring(l))
                .requestURL(url)
                .pathInfo(url.substring(l))
                .headers(getHeaders(request))
                .method(method)
                .contentType(ct)
                .contentLength(cl)
                // We need to read attribute after doComet
                .destroyable(false)
                .attributes(attributes)
                .servletPath(config.mappingPath())
                .session(session)
                .cookies(getCookies(request))
                .queryStrings(qs)
                .remoteInetSocketAddress(new Callable<InetSocketAddress>() {
                    @Override
                    public InetSocketAddress call() throws Exception {
                        return (InetSocketAddress) ctx.getChannel().getRemoteAddress();
                    }
                })
                .localInetSocketAddress(new Callable<InetSocketAddress>() {

                    @Override
                    public InetSocketAddress call() throws Exception {
                        return (InetSocketAddress) ctx.getChannel().getLocalAddress();
                    }
                });

        ChannelBuffer internalBuffer = request.getContent();
        if (!config.aggregateRequestBodyInMemory() && !method.equalsIgnoreCase("GET")) {
            return requestBuilder.body(internalBuffer.array()).build();
        } else {
            logger.trace("Unable to read in memory the request's bytes. Using stream");
            return requestBuilder.inputStream(new ChannelBufferInputStream(internalBuffer)).build();
        }
    }

    private void handleHttp(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {

        if (config.webSocketOnly()) {
            logger.trace("Forbiden {}", ctx);
            super.sendError(ctx, FORBIDDEN, messageEvent);
            return;
        }

        boolean skipClose = false;
        AtmosphereResponse response = null;
        AtmosphereRequest request = null;
        Action a = null;
        boolean resumeOnBroadcast = false;
        boolean keptOpen = false;
        ChannelWriter asyncWriter = null;
        String method = "GET";
        boolean writeHeader = false;
        boolean forceSuspend = false;
        boolean aggregateBodyInMemory = config.aggregateRequestBodyInMemory();

        try {
            if (messageEvent.getMessage() instanceof HttpRequest) {
                final HttpRequest hrequest = (HttpRequest) messageEvent.getMessage();
                boolean ka = HttpHeaders.isKeepAlive(hrequest);
                asyncWriter = config.supportChunking() ?
                        new ChunkedWriter(ctx.getChannel(), true, ka, channelBufferPool) :
                        new StreamWriter(ctx.getChannel(), true, ka);

                method = hrequest.getMethod().getName();

                // First let's try to see if it's a static resources
                if (!hrequest.getUri().contains(HeaderConfig.X_ATMOSPHERE)) {
                    try {
                        hrequest.headers().add(STATIC_MAPPING, "true");
                        super.messageReceived(ctx, messageEvent);

                        if (HttpHeaders.getHeader(hrequest, SERVICED) != null) {
                            return;
                        }
                    } catch (Exception e) {
                        logger.debug("Unexpected State", e);
                    } finally {
                        hrequest.headers().set(STATIC_MAPPING, "false");
                    }
                }

                request = createAtmosphereRequest(ctx, hrequest);
                request.setAttribute(KEEP_ALIVE, new Boolean(ka));

                // Hacky. Is the POST doesn't contains a body, we must not close the connection yet.
                AtmosphereRequestImpl.Body b = request.body();
                if (!aggregateBodyInMemory
                        && !hrequest.getMethod().equals(GET)
                        && !b.isEmpty()
                        && (b.hasString() && b.asString().isEmpty())
                        || (b.hasBytes() && b.asBytes().length == 0)) {
                    forceSuspend = true;
                }
            } else {
                request = State.class.cast(ctx.getChannel().getAttachment()).request;
                boolean isLast = HttpChunk.class.cast(messageEvent.getMessage()).isLast();
                Boolean ka = (Boolean) request.getAttribute(KEEP_ALIVE);

                asyncWriter = config.supportChunking() ?
                        new ChunkedWriter(ctx.getChannel(), isLast, ka, channelBufferPool) :
                        new StreamWriter(ctx.getChannel(), isLast, ka);
                method = request.getMethod();
                ChannelBuffer internalBuffer = HttpChunk.class.cast(messageEvent.getMessage()).getContent();

                if (!aggregateBodyInMemory && internalBuffer.hasArray()) {
                    request.body(internalBuffer.array());
                } else {
                    logger.trace("Unable to read in memory the request's bytes. Using stream");
                    request.body(new ChannelBufferInputStream(internalBuffer));
                }

                if (!isLast) {
                    forceSuspend = true;
                }
            }

            response = new AtmosphereResponseImpl.Builder()
                    .asyncIOWriter(asyncWriter)
                    .writeHeader(writeHeader)
                    .destroyable(false)
                    .header("Connection", "Keep-Alive")
                    .header("Server", "Nettosphere/2.0")
                    .request(request).build();

            if (config.supportChunking()) {
                response.setHeader("Transfer-Encoding", "chunked");
            }

            a = framework.doCometSupport(request, response);
            if (forceSuspend) {
                a.type(Action.TYPE.SUSPEND);
                // leave the stream open
                keptOpen = true;
            }

            String transport = (String) request.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport == null) {
                transport = request.getHeader(X_ATMOSPHERE_TRANSPORT);
            }

            if (a.type() == Action.TYPE.SUSPEND) {
                if (transport != null && (transport.equalsIgnoreCase(HeaderConfig.STREAMING_TRANSPORT)
                        || transport.equalsIgnoreCase(SSE_TRANSPORT))) {
                    keptOpen = true;
                } else if (transport != null && (
                        transport.equalsIgnoreCase(HeaderConfig.LONG_POLLING_TRANSPORT) ||
                                transport.equalsIgnoreCase(HeaderConfig.JSONP_TRANSPORT))) {
                    resumeOnBroadcast = true;
                }
            }

            final Action action = (Action) request.getAttribute(NettyCometSupport.SUSPEND);
            final State state = new State(request, action == null ? Action.CONTINUE : action);
            ctx.getChannel().setAttachment(state);

            if (action != null && action.type() == Action.TYPE.SUSPEND) {
                if (action.timeout() != -1) {
                    final AtomicReference<ChannelWriter> w = new AtomicReference<ChannelWriter>(asyncWriter);
                    final AtomicReference<Future<?>> f = new AtomicReference<Future<?>>();
                    f.set(suspendTimer.scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                            if (!w.get().isClosed() && (System.currentTimeMillis() - w.get().lastTick()) > action.timeout()) {
                                AtmosphereResourceImpl impl = state.resource();
                                if (impl != null) {
                                    asynchronousProcessor.endRequest(impl, false);
                                    f.get().cancel(true);
                                }
                            }
                        }
                    }, action.timeout(), action.timeout(), TimeUnit.MILLISECONDS));
                }
            } else if (action != null && action.type() == Action.TYPE.RESUME) {
                resumeOnBroadcast = false;
            }
        } catch (AtmosphereMappingException ex) {
            if (method.equalsIgnoreCase("GET")) {
                logger.trace("Unable to map the request {}, trying static file", messageEvent.getMessage());
                try {
                    skipClose = true;
                    super.messageReceived(ctx, messageEvent);
                } catch (Exception e) {
                    logger.error("Unable to process request", e);
                    throw new IOException(e);
                }
            }
        } catch (Throwable e) {
            logger.error("Unable to process request", e);
            throw new IOException(e);
        } finally {
            try {
                if (asyncWriter != null && !resumeOnBroadcast && !keptOpen) {
                    if (!skipClose && response != null) {
                        asyncWriter.close(response);
                    } else {
                        httpChannels.add(ctx.getChannel());
                    }
                }
            } finally {
                if (request != null && a != null && a.type() != Action.TYPE.SUSPEND) {
                    request.destroy();
                    response.destroy();
                    framework.notify(Action.TYPE.DESTROYED, request, response);
                }
            }
        }
    }

    @Override
    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, MessageEvent e) {
        // For websocket, we can't send an error
        if (websocketChannels.contains(ctx.getChannel())) {
            logger.debug("Error {} for {}", status, e);
            ctx.getChannel().close().addListener(ChannelFutureListener.CLOSE);
        } else if (e != null) {
            final HttpRequest request = (HttpRequest) e.getMessage();
            if (HttpHeaders.getHeader(request, STATIC_MAPPING, "false").equalsIgnoreCase("false")) {
                super.sendError(ctx, status, e);
            }
        } else {
            super.sendError(ctx, status, e);
        }
    }

    public void destroy() {
        isShutdown.set(true);
        if (framework != null) framework.destroy();

        httpChannels.close();
        websocketChannels.write(new CloseWebSocketFrame());
        suspendTimer.shutdown();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        websocketChannels.remove(e.getChannel());
        Object o = ctx.getChannel().getAttachment();
        ctx.getChannel().setAttachment(null);

        logger.trace("Attachment of {} was {}", e.getChannel().getId(), o);
        if (o instanceof WebSocket) {
            NettyWebSocket webSocket = NettyWebSocket.class.cast(o);
            logger.trace("Closing {} at {}", webSocket.uuid(), address(e.getChannel()));

            try {
                if (webSocket.closeFuture() != null) {
                    webSocket.closeFuture().cancel(true);
                }
                webSocketProcessor.close(webSocket, 1005);
                webSocket.recycle();
            } catch (Exception ex) {
                logger.error("Exception on {} at {}", webSocket.uuid(), address(e.getChannel()), ex);
            }
        } else if ( o != null && State.class.isAssignableFrom(o.getClass())) {
            logger.trace("State {}", o);
            State s = State.class.cast(o);
            if (s.action.type() == Action.TYPE.SUSPEND) {
                asynchronousProcessor.endRequest(s.resource(), true);
            }
        } else {
            // Will be null if handled by exceptionCaught
            if (o != null) {
                logger.warn("Invalid attachment {} and Channel {} at {}", o, ctx.getChannel(), address(e.getChannel()));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {

        try {
            if (e.getCause() != null
                    && (e.getCause().getClass().equals(ClosedChannelException.class)
                    || e.getCause().getClass().equals(IOException.class))) {
                logger.trace("Unexpected I/O Exception", e.getCause());
            } else if (e.getCause() != null && e.getCause().getClass().equals(TooLongFrameException.class)) {
                logger.error("TooLongFrameException. The request will be closed, make sure you increase the Config.maxChunkContentLength() to a higher value.", e.getCause());
                super.exceptionCaught(ctx, e);
            } else {
                logger.error("Unexpected and unhandled I/O Exception", e);
                super.exceptionCaught(ctx, e);
            }
        } finally {
            try {
                ctx.getChannel().close();
            } catch (Exception ex) {
                logger.trace("", ex);
            }
        }
    }

    private Map<String, String> getHeaders(final HttpRequest request) {
        final Map<String, String> headers = new HashMap<String, String>();

        for (String name : request.headers().names()) {
            // TODO: Add support for multi header
            headers.put(name, HttpHeaders.getHeader(request, name));
        }

        return headers;
    }

    private String address(Channel channel) {
        return ((InetSocketAddress) channel.getRemoteAddress()).getAddress().getHostAddress();
    }

    private String getBaseUri(final HttpRequest request) {
        return "http://" + HttpHeaders.getHeader(request, HttpHeaders.Names.HOST, "127.0.0.1") + "/";

    }

    private void parseQueryString(Map<String, String[]> qs, String queryString) {
        if (queryString != null) {
            String[] s = queryString.split("&");
            for (String a : s) {
                String[] q = a.split("=");
                String[] z = new String[]{q.length > 1 ? q[1] : ""};
                qs.put(q[0], z);
            }
        }
    }

    private Set<javax.servlet.http.Cookie> getCookies(final HttpRequest request) {
        Set<javax.servlet.http.Cookie> result = new HashSet<javax.servlet.http.Cookie>();
        String cookieHeader = request.headers().get("Cookie");
        if (cookieHeader != null) {
            Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieHeader);
            for (Cookie cookie : cookies) {
                javax.servlet.http.Cookie c = new javax.servlet.http.Cookie(cookie.name(), cookie.value());
// Netty 3.10.2
//                if (cookie.getComment() != null) {
//                    c.setComment(cookie.getComment());
//                }

                if (cookie.domain() != null) {
                    c.setDomain(cookie.domain());
                }

                c.setHttpOnly(cookie.isHttpOnly());
                c.setMaxAge((int) cookie.maxAge());

                if (cookie.path() != null) {
                    c.setPath(cookie.path());
                }

                c.setSecure(cookie.isSecure());
// Netty 3.10.2
//                c.setVersion(cookie.getVersion());
                result.add(c);

            }
        }
        return result;
    }

    Config config() {
        return config;
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.getChannel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    public final static class State {
        final AtmosphereRequest request;
        final Action action;

        public State(AtmosphereRequest request, Action action) {
            this.request = request;
            this.action = action;
        }

        public AtmosphereResourceImpl resource() {
            return AtmosphereResourceImpl.class.cast(request.resource());
        }
    }

    private String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.headers().get(HttpHeaders.Names.HOST) + req.getUri();
    }

    private final static class NettyServletConfig implements ServletConfig {

        private final Map<String, String> initParams;
        private final ServletContext ctx;

        public NettyServletConfig(Map<String, String> initParams, ServletContext ctx) {
            this.initParams = initParams;
            this.ctx = ctx;
        }

        @Override
        public String getServletName() {
            return "Netty";
        }

        @Override
        public ServletContext getServletContext() {
            return ctx;
        }

        @Override
        public String getInitParameter(String name) {
            return initParams.get(name);
        }

        @Override
        public Enumeration getInitParameterNames() {
            return Collections.enumeration(initParams.keySet());
        }
    }

    public ChannelGroup httpChannels() {
        return httpChannels;
    }

    public ChannelGroup websocketChannels() {
        return websocketChannels;
    }

    public ChannelBufferPool channelBufferPool() {
        return channelBufferPool;
    }
}
