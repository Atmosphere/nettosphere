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

import org.atmosphere.container.NettyCometSupport;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereMappingException;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.nettosphere.util.Version;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.HeaderConfig.SSE_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Bridge the Atmosphere Framework with Netty.
 */
public class NettyAtmosphereHandler extends HttpStaticFileServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyAtmosphereHandler.class);
    private final AtmosphereFramework framework;
    private final Config config;
    private WebSocketServerHandshaker handshaker;
    private final ScheduledExecutorService suspendTimer;

    public NettyAtmosphereHandler(Config config) {
        super(config.path());
        this.config = config;
        framework = new AtmosphereFramework();

        if (config.broadcaster() != null) {
            framework.setDefaultBroadcasterClassName(config.broadcaster().getName());
        }

        framework.setAtmosphereDotXmlPath(config.configFile());
        framework.setAsyncSupport(new NettyCometSupport(framework.getAtmosphereConfig()));
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

        if (config.webSocketProtocol() != null) {
            framework.setWebSocketProtocolClassName(config.webSocketProtocol().getName());
        }

        for (AtmosphereInterceptor i : config.interceptors()) {
            framework.interceptor(i);
        }

        framework.setLibPath(config.libPath());

        try {
            framework.init(new NettyServletConfig(config.initParams(), new Context.Builder().contextPath(config.mappingPath()).basePath(config.path()).build()));
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        suspendTimer = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        Object msg = messageEvent.getMessage();
        if (msg instanceof HttpRequest) {
            HttpRequest r = HttpRequest.class.cast(msg);
            // Netty fail to decode headers separated by a ','
            List<String> c = r.getHeaders("Connection");
            String u = r.getHeader("Upgrade");
            boolean webSocket = false;
            if (u != null && u.equalsIgnoreCase("websocket")) {
                webSocket = true;
            }

            for (String connection : c) {
                if (connection != null && connection.toLowerCase().equalsIgnoreCase("upgrade")) {
                    webSocket = true;
                }
            }

            logger.debug("Handling request {}", r);

            if (webSocket) {
                handleWebSocketHandshake(ctx, messageEvent);
            } else {
                handleHttp(ctx, messageEvent);
            }
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, messageEvent);
        }
    }

    private void handleWebSocketHandshake(ChannelHandlerContext ctx, MessageEvent messageEvent) throws IOException, URISyntaxException {
        final HttpRequest request = (HttpRequest) messageEvent.getMessage();

        // Allow only GET methods.
        if (request.getMethod() != GET) {
            sendHttpResponse(ctx, request, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(request), null, false);
        handshaker = wsFactory.newHandshaker(request);

        if (this.handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        } else {
            this.handshaker.handshake(ctx.getChannel(), request);
        }

        WebSocketProcessor processor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(framework);
        AtmosphereRequest r = createAtmosphereRequest(ctx, request);
        WebSocket webSocket = new NettyWebSocket(ctx.getChannel(), framework.getAtmosphereConfig());

        ctx.setAttachment(new Attachment(processor, webSocket));
        processor.open(webSocket, r, AtmosphereResponse.newInstance(framework.getAtmosphereConfig(), r, webSocket));
    }

    private final static class Attachment {

        public final WebSocketProcessor p;
        public final WebSocket w;

        private Attachment(WebSocketProcessor p, WebSocket w) {
            this.p = p;
            this.w = w;
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        WebSocketFrame frame = (WebSocketFrame) messageEvent.getMessage();

        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
        } else if (frame instanceof BinaryWebSocketFrame) {
            Attachment a = (Attachment) ctx.getAttachment();
            WebSocketProcessor processor = a.p;
            ChannelBuffer binaryData = frame.getBinaryData();
            processor.invokeWebSocketProtocol(a.w, binaryData.array(), binaryData.arrayOffset(), binaryData.readableBytes());
        } else if (frame instanceof TextWebSocketFrame) {
            Attachment a = (Attachment) ctx.getAttachment();
            WebSocketProcessor processor = a.p;
            processor.invokeWebSocketProtocol(a.w, ((TextWebSocketFrame) frame).getText());
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }
    }

    private AtmosphereRequest createAtmosphereRequest(final ChannelHandlerContext ctx, HttpRequest request) throws URISyntaxException, UnsupportedEncodingException, MalformedURLException {
        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.getUri());
        String ct = request.getHeaders("Content-Type").size() > 0 ? request.getHeaders("Content-Type").get(0) : "text/plain";
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
        int last = u.indexOf("?") == -1 ? u.length() : u.indexOf("?");
        String url = u.substring(0, last);
        int l;

        if (url.contains(config.mappingPath())) {
            l =  requestUri.getAuthority().length() + requestUri.getScheme().length() + 3 + config.mappingPath().length();
        } else {
            l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3;
        }


        final Map<String, Object> attributes = new HashMap<String, Object>();

        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        AtmosphereRequest r = requestBuilder.requestURI(url.substring(l))
                .requestURL(url)
                .pathInfo(url.substring(l))
                .headers(getHeaders(request))
                .method(method)
                .contentType(ct)
                .destroyable(false)
                .attributes(attributes)
                .servletPath(config.mappingPath())
                .queryStrings(qs)
                .remotePort(((InetSocketAddress) ctx.getChannel().getRemoteAddress()).getPort())
                .remoteAddr(((InetSocketAddress) ctx.getChannel().getRemoteAddress()).getAddress().getHostAddress())
                .remoteHost(((InetSocketAddress) ctx.getChannel().getRemoteAddress()).getHostName())
                .localPort(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getPort())
                .localAddr(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getAddress().getHostAddress())
                .localName(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getHostName())
                .inputStream(new ChannelBufferInputStream(request.getContent()))
                .build();

        return r;
    }

    private void handleHttp(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        final ChannelAsyncIOWriter w = new ChannelAsyncIOWriter(ctx.getChannel(), true);
        boolean resumeOnBroadcast = false;
        boolean keptOpen = false;
        final HttpRequest request = (HttpRequest) messageEvent.getMessage();
        String method = request.getMethod().getName();

        // First let's try to see if it's a static resources
        try {
            request.addHeader(STATIC_MAPPING, "true");
            super.messageReceived(ctx, messageEvent);

            if (request.getHeader(SERVICED) != null) {
                return;
            }
        } catch (Exception e) {
            logger.debug("", e);
        } finally {
            request.addHeader(STATIC_MAPPING, "false");
        }

        boolean skipClose = false;
        try {
            AtmosphereRequest r = createAtmosphereRequest(ctx, request);

            AtmosphereResponse response = new AtmosphereResponse.Builder()
                    .writeHeader(true)
                    .asyncIOWriter(w)
                    .writeHeader(false)
                    .header("Connection", "Keep-Alive")
                    .header("Transfer-Encoding", "chunked")
                    .header("Server", "Nettosphere-" + Version.getRawVersion())
                    .request(r).build();

            r.setAttribute(NettyCometSupport.CHANNEL, w);

            Action a = framework.doCometSupport(r, response);

            final AsynchronousProcessor.AsynchronousProcessorHook hook = (AsynchronousProcessor.AsynchronousProcessorHook) r.getAttribute(FrameworkConfig.ASYNCHRONOUS_HOOK);

            String transport = (String) r.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport == null) {
                transport = r.getHeader(X_ATMOSPHERE_TRANSPORT);
            }

            if (a.type() == Action.TYPE.SUSPEND) {
                if (transport != null && (transport.equalsIgnoreCase(HeaderConfig.STREAMING_TRANSPORT)
                        || transport.equalsIgnoreCase(SSE_TRANSPORT))) {
                    keptOpen = true;
                } else if (transport != null && transport.equalsIgnoreCase(HeaderConfig.LONG_POLLING_TRANSPORT)) {
                    resumeOnBroadcast = true;
                }
            }

            final Action action = (Action) r.getAttribute(NettyCometSupport.SUSPEND);
            if (action != null && action.type() == Action.TYPE.SUSPEND && action.timeout() != -1) {
                final AtomicReference<Future<?>> f = new AtomicReference();
                f.set(suspendTimer.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if (!w.isClosed() && (System.currentTimeMillis() - w.lastTick()) > action.timeout()) {
                            hook.timedOut();
                            f.get().cancel(true);
                        }
                    }
                }, action.timeout(), action.timeout(), TimeUnit.MILLISECONDS));
            } else if (action != null && action.type() == Action.TYPE.RESUME) {
                resumeOnBroadcast = false;
            }
            w.resumeOnBroadcast(resumeOnBroadcast);
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
            if (w != null && !resumeOnBroadcast && !keptOpen) {
                if (!w.byteWritten()) {
                    w.writeError((AtmosphereResponse) null, 200, "OK");
                }
                if (!skipClose) {
                    w.close((AtmosphereResponse) null);
                }
            }
        }
    }

    @Override
    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, MessageEvent e) {
        if (e != null) {
            final HttpRequest request = (HttpRequest) e.getMessage();
            if (request.getHeader(STATIC_MAPPING) == null || request.getHeader(STATIC_MAPPING).equalsIgnoreCase("false")) {
                super.sendError(ctx, status, e);
            }
        } else {
            super.sendError(ctx, status, e);
        }
    }

    public void destroy() {
        if (framework != null) framework.destroy();
        suspendTimer.shutdown();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelClosed(ctx, e);
        Attachment o = (Attachment) ctx.getAttachment();
        if (o == null) return;

        WebSocket w = o.w;
        AsynchronousProcessor.AsynchronousProcessorHook hook = AsynchronousProcessor.AsynchronousProcessorHook.class.cast(
                w.resource().getRequest().getAttribute(FrameworkConfig.ASYNCHRONOUS_HOOK));
        if (hook != null) {
            hook.closed();
        }
        o.p.close(w, 1000);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        logger.debug("Exception", e.getCause());
        super.exceptionCaught(ctx, e);
    }

    private Map<String, String> getHeaders(final HttpRequest request) {
        final Map<String, String> headers = new HashMap<String, String>();

        for (String name : request.getHeaderNames()) {
            // TODO: Add support for multi header
            headers.put(name, request.getHeaders(name).get(0));
        }

        return headers;
    }

    private String getBaseUri(final HttpRequest request) {
        return "http://" + request.getHeader(HttpHeaders.Names.HOST) + "/";

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

    private String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + req.getUri();
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


}
