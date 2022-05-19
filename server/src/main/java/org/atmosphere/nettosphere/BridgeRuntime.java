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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.ImmediateEventExecutor;
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
import org.atmosphere.nettosphere.util.Version;
import org.atmosphere.util.FakeHttpSession;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketEventListener;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketPingPongListener;
import org.atmosphere.websocket.WebSocketProcessor;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelHandler.Sharable;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.atmosphere.cpr.ApplicationConfig.ALLOW_WEBSOCKET_STATUS_CODE_1005_AS_DISCONNECT;
import static org.atmosphere.cpr.AtmosphereFramework.REFLECTOR_ATMOSPHEREHANDLER;
import static org.atmosphere.cpr.HeaderConfig.SSE_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;
import static org.atmosphere.websocket.WebSocketEventListener.WebSocketEvent.TYPE.HANDSHAKE;

/**
 * Bridge the Atmosphere Framework with Netty.
 *
 * @author Jeanfrancois Arcand
 */
@Sharable
public class BridgeRuntime extends HttpStaticFileServerHandler {

    public static boolean NETTY_41_PLUS;

    static {
        try {
            Class.forName("io.netty.channel.ChannelId");
            NETTY_41_PLUS = true;
        } catch (ClassNotFoundException e) {
            NETTY_41_PLUS = false;
        }
    }

    private final static String KEEP_ALIVE = BridgeRuntime.class.getName() + "_keep-alive";
    private static final Logger logger = LoggerFactory.getLogger(BridgeRuntime.class);

    private final AtmosphereFramework framework;
    private final Config config;
    private final ScheduledExecutorService suspendTimer;
    private final ConcurrentHashMap<String, HttpSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean isShutdown = new AtomicBoolean();
    private final WebSocketProcessor webSocketProcessor;
    private final ChannelGroup httpChannels = new DefaultChannelGroup("http", ImmediateEventExecutor.INSTANCE);
    private final ChannelGroup websocketChannels = new DefaultChannelGroup("ws", ImmediateEventExecutor.INSTANCE);
    private final AsynchronousProcessor asynchronousProcessor;
    private final int maxWebSocketFrameSize;

    private final AtmosphereRequest proxiedRequest;
    private final AtmosphereResponse proxiedResponse;
    private final AtmosphereResource proxiedResource;

    private int webSocketTimeout = -1;
    private final byte[] EMPTY = new byte[0];

    public BridgeRuntime(final Config config) {
        super(config);
        this.config = config;
        framework = new AtmosphereFramework();
        framework.addInitParameter(ALLOW_WEBSOCKET_STATUS_CODE_1005_AS_DISCONNECT, "true");

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
    public void channelRead(final ChannelHandlerContext ctx, final Object messageEvent) throws URISyntaxException, IOException {
        try {
            handleMessageEvent(ctx, messageEvent);
        } finally {
            if (messageEvent instanceof ReferenceCounted) {
                ReferenceCounted refMsg = (ReferenceCounted) messageEvent;
                if (refMsg.refCnt() > 0)
                    refMsg.release();
            }
        }
    }

    private void handleMessageEvent(final ChannelHandlerContext ctx, final Object messageEvent) throws URISyntaxException, IOException {
        Object msg = messageEvent;

        if (isShutdown.get()) {
            ctx.channel().close().addListener(ChannelFutureListener.CLOSE);
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

            logger.trace("Handling request {}", r);
            if (webSocket) {
                handleWebSocketHandshake(ctx, messageEvent);
            } else {
                if (config.webSocketOnly()) {
                    logger.trace("Forbidenn {}", ctx);
                    sendHttpResponse(ctx, r, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
                    return;
                }

                handleHttp(ctx, messageEvent);
            }
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, messageEvent);
        } else if (msg instanceof HttpChunkedInput) {
            handleHttp(ctx, messageEvent);
        }
    }

    private void handleWebSocketHandshake(final ChannelHandlerContext ctx, Object messageEvent) throws IOException, URISyntaxException {
        final HttpRequest request = (HttpRequest) messageEvent;

        // Allow only GET methods.
        if (request.getMethod() != GET) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        ctx.pipeline().addBefore(BridgeRuntime.class.getName(), "encoder", new HttpResponseEncoder());

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(request),
                config.subProtocols(),
                false,
                maxWebSocketFrameSize);

        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(request);

        if (handshaker == null) {
            wsFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            final NettyWebSocket webSocket = new NettyWebSocket(ctx.channel(), framework.getAtmosphereConfig(), config.noInternalAlloc(), config.binaryWrite(), config.maxWebSocketFrameSize());
            final AtmosphereRequest atmosphereRequest = createAtmosphereRequest(ctx, request, EMPTY);

            if (!webSocketProcessor.handshake(atmosphereRequest)) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST, null);
                return;
            }

            webSocketProcessor.notifyListener(webSocket, new WebSocketEventListener.WebSocketEvent("", HANDSHAKE, webSocket));

            handshaker.handshake(ctx.channel(), (FullHttpRequest) request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        future.channel().close();
                    } else {
                        websocketChannels.add(ctx.channel());

                        ctx.channel().attr(ATTACHMENT).set(webSocket);
                        if (config.noInternalAlloc()) {
                            webSocket.resource(proxiedResource);
                        }

                        AtmosphereResponse response = config.noInternalAlloc() ? proxiedResponse :
                                AtmosphereResponseImpl.newInstance(framework.getAtmosphereConfig(), atmosphereRequest, webSocket);
                        webSocketProcessor.open(webSocket, atmosphereRequest, response);

                        if (webSocketTimeout > 0) {
                            webSocket.closeFuture(suspendTimer.scheduleAtFixedRate(() -> {
                                if (webSocket.lastWriteTimeStampInMilliseconds() != 0 && (System.currentTimeMillis() - webSocket.lastWriteTimeStampInMilliseconds() > webSocketTimeout)) {
                                    logger.debug("Timing out {}", webSocket);
                                    webSocket.close();
                                }
                            }, webSocketTimeout, webSocketTimeout, TimeUnit.MILLISECONDS));
                        }
                    }
                }
            });
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final Object messageEvent) {
        WebSocketFrame frame = (WebSocketFrame) messageEvent;

        logger.trace("Received frame {}", frame.getClass().getName());

        // Check for closing frame
        WebSocket attachment = WebSocket.class.cast(ctx.channel().attr(ATTACHMENT).get());
        ByteBuf binaryData = frame.content();
        byte[] body = null;
        if ((config.textFrameAsBinary() || !(frame instanceof TextWebSocketFrame)) && binaryData.isReadable()) {
            body = new byte[binaryData.readableBytes()];
            binaryData.readBytes(body);
        }
        if (frame instanceof CloseWebSocketFrame) {
            ctx.channel().write(frame).addListener(ChannelFutureListener.CLOSE);
        } else if (frame instanceof PingWebSocketFrame) {
            if (body != null && WebSocketPingPongListener.class.isAssignableFrom(webSocketProcessor.getClass())) {
                WebSocketPingPongListener.class.cast(webSocketProcessor).onPing(attachment, body, 0, body.length);
            } else {
                ctx.channel().writeAndFlush(new PongWebSocketFrame(binaryData));
            }
        } else if (frame instanceof BinaryWebSocketFrame || (frame instanceof TextWebSocketFrame && config.textFrameAsBinary())) {
            webSocketProcessor.invokeWebSocketProtocol(attachment, body, 0, body.length);
        } else if (frame instanceof TextWebSocketFrame) {
            webSocketProcessor.invokeWebSocketProtocol(attachment, ((TextWebSocketFrame) frame).text());
        } else if (frame instanceof PongWebSocketFrame) {

            if (body !=null && WebSocketPingPongListener.class.isAssignableFrom(webSocketProcessor.getClass())) {
                WebSocketPingPongListener.class.cast(webSocketProcessor).onPong(attachment, body, 0, body.length);
            }

            if (config.enablePong()) {
                ctx.channel().writeAndFlush(new PingWebSocketFrame(frame.content()));
            } else {
                logger.trace("Received Pong Frame on Channel {}", ctx.channel());
            }
        } else {
            logger.warn("{} frame types not supported", frame.getClass());
            ctx.channel().close();
        }
    }

    private AtmosphereRequest createAtmosphereRequest(final ChannelHandlerContext ctx, final HttpRequest request, byte[] body) throws URISyntaxException, UnsupportedEncodingException, MalformedURLException {
        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.getUri());
        final String ct = HttpHeaders.getHeader(request, "Content-Type", "text/plain");
        final long cl = HttpHeaders.getContentLength(request, 0);
        String method = request.getMethod().name();

        String queryString = requestUri.getQuery();
        Map<String, String[]> qs = new HashMap<String, String[]>();
        if (queryString != null) {
            parseQueryString(qs, queryString);
        }

        if (ct.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            if (FullHttpRequest.class.isAssignableFrom(request.getClass())) {
                parseQueryString(qs, new String(body));
            }
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
                .remoteInetSocketAddress((Callable<InetSocketAddress>) () -> (InetSocketAddress) ctx.channel().remoteAddress())
                .localInetSocketAddress((Callable<InetSocketAddress>) () -> (InetSocketAddress) ctx.channel().localAddress());

        if (body.length > 0) {
            requestBuilder.body(body);
        }

        return requestBuilder.build();

    }

    private void handleHttp(final ChannelHandlerContext ctx, final Object messageEvent) throws IOException {

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

        boolean supportChunking = config.supportChunking();

        try {
            if (messageEvent instanceof HttpRequest) {
                final HttpRequest hrequest = (HttpRequest) messageEvent;

                byte[] body = EMPTY;
                if (FullHttpRequest.class.isAssignableFrom(messageEvent.getClass())) {
                    ByteBuf b = FullHttpRequest.class.cast(messageEvent).content();
                    if (b.isReadable()) {
                        body = new byte[b.readableBytes()];
                        b.readBytes(body);
                    }
                }

                // First let's try to see if it's a static resources
                if (!hrequest.getUri().contains(HeaderConfig.X_ATMOSPHERE)) {
                    try {
                        hrequest.headers().add(STATIC_MAPPING, "true");
                        super.channelRead0(ctx, (FullHttpRequest) messageEvent);

                        if (HttpHeaders.getHeader(hrequest, SERVICED) != null) {
                            return;
                        }
                    } catch (Exception e) {
                        logger.debug("Unexpected State", e);
                    } finally {
                        hrequest.headers().set(STATIC_MAPPING, "false");
                    }
                }

                boolean ka = HttpHeaders.isKeepAlive(hrequest);

                // Disable Chunking for Http/1.0
                if (hrequest.protocolVersion().majorVersion() == 1 && hrequest.protocolVersion().minorVersion() == 0) {
                    supportChunking = false;
                }

                asyncWriter = supportChunking ?
                        new ChunkedWriter(ctx.channel(), true, ka) :
                        new StreamWriter(ctx.channel(), true, ka);

                method = hrequest.getMethod().name();

                request = createAtmosphereRequest(ctx, hrequest, body);
                request.setAttribute(KEEP_ALIVE, ka);

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
                request = State.class.cast(ctx.attr(ATTACHMENT).get()).request;
                boolean isLast = HttpChunkedInput.class.cast(messageEvent).isEndOfInput();
                Boolean ka = (Boolean) request.getAttribute(KEEP_ALIVE);

                asyncWriter = config.supportChunking() ?
                        new ChunkedWriter(ctx.channel(), isLast, ka) :
                        new StreamWriter(ctx.channel(), isLast, ka);
                method = request.getMethod();
                ByteBuf internalBuffer = HttpChunkedInput.class.cast(messageEvent).readChunk(ctx).content();

                if (!aggregateBodyInMemory && internalBuffer.hasArray()) {
                    request.body(internalBuffer.array());
                } else {
                    logger.trace("Unable to read in memory the request's bytes. Using stream");
                    request.body(new ByteBufInputStream(internalBuffer));
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
                    .header("Server", "Nettosphere/" + Version.getDotedVersion())
                    .request(request).build();


            if (supportChunking) {
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

            ctx.attr(ATTACHMENT).set(state);

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
                logger.trace("Unable to map the request {}, trying static file", messageEvent);
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
                        httpChannels.add(ctx.channel());
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
    public void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, FullHttpRequest e) {
        // For websocket, we can't send an error
        logger.trace("Error {} for {}", status, e);
        if (websocketChannels.contains(ctx.channel())) {
            logger.debug("Error {} for {}", status, e);
            ctx.channel().close().addListener(ChannelFutureListener.CLOSE);
        } else if (e != null) {
            if (HttpHeaders.getHeader(e, STATIC_MAPPING, "false").equalsIgnoreCase("false")) {
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Object o = ctx.attr(ATTACHMENT).get();

        if (o == null) return;

        if (WebSocket.class.isAssignableFrom(o.getClass())) {
            NettyWebSocket webSocket = NettyWebSocket.class.cast(o);
            logger.trace("Closing {}", webSocket.uuid());

            try {
                if (webSocket.closeFuture() != null) {
                    webSocket.closeFuture().cancel(true);
                }

                webSocketProcessor.close(webSocket, 1005);
            } catch (Exception ex) {
                logger.error("{}", webSocket, ex);
            }
        } else if (State.class.isAssignableFrom(o.getClass())) {
            logger.trace("State {}", o);
            State s = State.class.cast(o);
            if (s.action.type() == Action.TYPE.SUSPEND) {
                asynchronousProcessor.endRequest(s.resource(), true);
            }
        } else {
            logger.warn("Invalid state {} and Channel {}", o, ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e){

        if (config.ioExceptionHandler().of(ctx, e)) {
            try {

                if (e instanceof IOException) {
                    logger.trace("Connection closed Exception", e);
                } else if (e.getCause() != null
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
                    ctx.channel().close();
                } catch (Exception ex) {
                    logger.trace("", ex);
                }
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
            Set<io.netty.handler.codec.http.cookie.Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieHeader);
            for (io.netty.handler.codec.http.cookie.Cookie cookie : cookies) {
                javax.servlet.http.Cookie c = new javax.servlet.http.Cookie(cookie.name(), cookie.value());

                if (cookie.domain() != null) {
                    c.setDomain(cookie.domain());
                }

                c.setHttpOnly(cookie.isHttpOnly());
                c.setMaxAge((int) cookie.maxAge());
                if (cookie.path() != null) {
                    c.setPath(cookie.path());
                }

                c.setSecure(cookie.isSecure());
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
        if (res.getStatus().code() != 200) {
            FullHttpResponse response = (FullHttpResponse) res;
            response.content().writeBytes(Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
            io.netty.handler.codec.http.HttpUtil.setContentLength(res, response.content().readableBytes());
            io.netty.handler.codec.http.HttpUtil.setKeepAlive(res, false);
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.getStatus().code() != 200) {
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

}
