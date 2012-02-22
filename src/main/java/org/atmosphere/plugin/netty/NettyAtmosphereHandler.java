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
package org.atmosphere.plugin.netty;

import org.atmosphere.container.NettyCometSupport;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereMappingException;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.util.Version;
import org.atmosphere.websocket.WebSocketProcessor;
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
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private final AtmosphereServlet as;
    private final Config config;
    private WebSocketServerHandshaker handshaker;
    private final ScheduledExecutorService suspendTimer;

    public NettyAtmosphereHandler(Config config) {
        super(config.path());
        this.config = config;
        as = new AtmosphereServlet();

        if (config.broadcaster() != null) {
            as.setDefaultBroadcasterClassName(config.broadcaster().getName());
        }

        try {
            if (config.broadcasterFactory() != null) {
                as.setBroadcasterFactory(config.broadcasterFactory());
            }
        } catch (Throwable t) {
            logger.trace("", t);
        }

        if (config.broadcasterCache() != null) {
            try {
                as.setBroadcasterCacheClassName(config.broadcasterCache().getName());
            } catch (Throwable t) {
                logger.trace("", t);
            }
        }

        Map<String, AtmosphereHandler<?, ?>> handlersMap = config.handlersMap();
        for (Map.Entry<String, AtmosphereHandler<?, ?>> e : handlersMap.entrySet()) {
            as.addAtmosphereHandler(e.getKey(), e.getValue());
        }

        try {
            as.init(new NettyServletConfig(config.initParams(), new NettyServletContext.Builder().basePath(config.path()).build()));
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        suspendTimer = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        Object msg = messageEvent.getMessage();
        if (msg instanceof HttpRequest) {
            String connection = HttpRequest.class.cast(msg).getHeader("Connection");
            if (connection != null && connection.equalsIgnoreCase("upgrade")) {
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

        WebSocketProcessor processor = new WebSocketProcessor(as,
                new NettyWebSocket(ctx.getChannel(), as.getAtmosphereConfig()),
                as.getWebSocketProtocol());
        ctx.setAttachment(processor);
        AtmosphereRequest r = createAtmosphereRequest(ctx, request);

        processor.dispatch(r);
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws URISyntaxException, IOException {
        WebSocketFrame frame = (WebSocketFrame) messageEvent.getMessage();

        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
            return;
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        } else if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }

        WebSocketProcessor processor = (WebSocketProcessor) ctx.getAttachment();
        processor.invokeWebSocketProtocol(((TextWebSocketFrame) frame).getText());
    }

    private AtmosphereRequest createAtmosphereRequest(final ChannelHandlerContext ctx, HttpRequest request) throws URISyntaxException, UnsupportedEncodingException, MalformedURLException {
        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + sanitizeUri(request.getUri()));
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
        int l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3;
        final Map<String, Object> attributes = new HashMap<String, Object>();

        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        AtmosphereRequest r = requestBuilder.requestURI(url.substring(l))
                .requestURL(url)
                .pathInfo(url.substring(l))
                .headers(getHeaders(request))
                .method(method)
                .contentType(ct)
                .attributes(attributes)
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
        final ChannelAsyncIOWriter w = new ChannelAsyncIOWriter(ctx.getChannel());
        boolean resumeOnBroadcast = false;
        boolean keptOpen = false;
        final HttpRequest request = (HttpRequest) messageEvent.getMessage();
        String method = request.getMethod().getName();

        boolean skipClose = false;
        try {
            AtmosphereRequest r = createAtmosphereRequest(ctx, request);

            AtmosphereResponse response = new AtmosphereResponse.Builder()
                    .writeHeader(true)
                    .asyncIOWriter(w)
                    .header("Connection", "Keep-Alive")
                    .header("Transfer-Encoding", "chunked")
                    .header("Server", "Atmosphere-" + Version.getRawVersion())
                    .atmosphereRequest(r).build();

            r.setAttribute(NettyCometSupport.CHANNEL, w);

            as.doCometSupport(r, response);

            NettyCometSupport.CometSupportHook hook = (NettyCometSupport.CometSupportHook) r.getAttribute(NettyCometSupport.HOOK);
            ctx.setAttachment(hook);

            String transport = (String) r.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport != null && transport.equalsIgnoreCase(HeaderConfig.STREAMING_TRANSPORT)) {
                keptOpen = true;
            } else if (transport != null && transport.equalsIgnoreCase(HeaderConfig.LONG_POLLING_TRANSPORT)) {
                resumeOnBroadcast = true;
            }

            AtmosphereServlet.Action action = (AtmosphereServlet.Action) r.getAttribute(NettyCometSupport.SUSPEND);

            if (action != null && action.type == AtmosphereServlet.Action.TYPE.SUSPEND) {
                suspendTimer.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!w.isClosed()) {
                                w.close();
                            }
                        } catch (IOException e) {
                            logger.trace("", e);
                        }
                    }
                }, action.timeout, TimeUnit.MILLISECONDS);
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
                    w.writeError(200, "OK");
                }
                if (!skipClose) {
                    w.close();
                }
            }
        }
    }

    public void destroy() {
        if (as != null) as.destroy();
        suspendTimer.shutdown();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelClosed(ctx, e);
        Object o = ctx.getAttachment();
        if (o == null) return;

        if (NettyCometSupport.CometSupportHook.class.isAssignableFrom(o.getClass())) {
            NettyCometSupport.CometSupportHook hook = NettyCometSupport.CometSupportHook.class.cast(o);
            if (hook != null) {
                hook.closed();
            }
        } else if (WebSocketProcessor.class.isAssignableFrom(o.getClass())) {
            WebSocketProcessor.class.cast(o).close();
        }
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
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST);
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
            return "AtmosphereServlet";
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
            return Collections.enumeration(initParams.values());
        }
    }


}
