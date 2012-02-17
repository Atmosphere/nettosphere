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

import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereMappingException;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.util.Version;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * Bridge the Atmosphere Framework with Netty.
 */
public class NettyAtmosphereHandler extends HttpStaticFileServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyAtmosphereHandler.class);
    private final AtmosphereServlet as;
    private final Config config;

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
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context, final MessageEvent messageEvent) throws URISyntaxException, IOException {

        ChannelAsyncIOWriter w = null;
        boolean resumeOnBroadcast = false;
        boolean keptOpen = false;
        final HttpRequest request = (HttpRequest) messageEvent.getMessage();
        String method = request.getMethod().getName();
        try {
            final String base = getBaseUri(request);
            final URI requestUri = new URI(base.substring(0, base.length() - 1) + sanitizeUri(request.getUri()));
            String ct = request.getHeaders("Content-Type").size() > 0 ? request.getHeaders("Content-Type").get(0) : "text/plain";

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
                    .inputStream(new ChannelBufferInputStream(request.getContent()))
                    .build();

            w = new ChannelAsyncIOWriter(context.getChannel());
            AtmosphereResponse response = new AtmosphereResponse.Builder()
                    .writeHeader(true)
                    .asyncIOWriter(w)
                    .header("Connection", "Keep-Alive")
                    .header("Server", "Atmosphere-" + Version.getRawVersion())
                    .atmosphereRequest(r).build();

            as.doCometSupport(r, response);

            String transport = (String) r.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport != null && transport.equalsIgnoreCase(HeaderConfig.STREAMING_TRANSPORT)) {
                keptOpen = true;
            } else if (transport != null && transport.equalsIgnoreCase(HeaderConfig.LONG_POLLING_TRANSPORT)) {
                resumeOnBroadcast = true;
            }

            w.resumeOnBroadcast(resumeOnBroadcast);
        } catch (AtmosphereMappingException ex) {
            if (method.equalsIgnoreCase("GET")) {
                logger.trace("Unable to map the request {}, trying static file", messageEvent.getMessage());
                try {
                    super.messageReceived(context, messageEvent);
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
                w.close();
            }
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

    private final static class NettyServletConfig implements ServletConfig {

        private final Map<String, String> initParams;
        private final ServletContext context;

        public NettyServletConfig(Map<String, String> initParams, ServletContext context) {
            this.initParams = initParams;
            this.context = context;
        }

        @Override
        public String getServletName() {
            return "AtmosphereServlet";
        }

        @Override
        public ServletContext getServletContext() {
            return context;
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
