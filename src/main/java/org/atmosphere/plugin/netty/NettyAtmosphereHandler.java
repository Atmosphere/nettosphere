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

import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.FrameworkConfig;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
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
import org.jboss.netty.buffer.ChannelBufferOutputStream;

class NettyAtmosphereHandler extends SimpleChannelUpstreamHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyAtmosphereHandler.class);
    private final AtmosphereServlet as;
    private final Map<String, String> initParams = new HashMap<String, String>();
    private final Config config;

    public NettyAtmosphereHandler(Config config) {
        super();
        this.config = config;
        as = new AtmosphereServlet();
        try {
            as.init(new NettyServletConfig(config.initParams(), new NettyServletContext.Builder().basePath(config.path()).build()));
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context,
                                final MessageEvent messageEvent) throws URISyntaxException, IOException {
        try {
            final HttpRequest request = (HttpRequest) messageEvent.getMessage();
            final String base = getBaseUri(request);
            final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.getUri());
            String ct = request.getHeaders("Content-Type").size() > 0 ? request.getHeaders("Content-Type").get(0) : "text/plain";
            String url = requestUri.toURL().toString();
            int l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3;
            final Map<String,Object> attributes = new HashMap<String, Object>();

            AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
            AtmosphereRequest r = requestBuilder.requestURI(url.substring(l))
                    .requestURL(url)
                    .headers(getHeaders(request))
                    .method(request.getMethod().getName())
                    .contentType(ct)
                    .attributes(attributes)
                    .inputStream(new ChannelBufferInputStream(request.getContent()))
                    .build();

            NettyWriter w = new NettyWriter(context.getChannel());
            AtmosphereResponse.Builder responseBuilder = new AtmosphereResponse.Builder()
                    .writeHeader(true)
                    .asyncIOWriter(w)
                    .header("Connection", "Keep-Alive")
                    .atmosphereRequest(r);

            as.doCometSupport(r, responseBuilder.build());

            try {
                w.flush();
            } finally {
                w.close();
            }

        } catch (ServletException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        // Close the connection when an exception is raised.
        logger.warn("Unexpected exception from downstream.", e.getCause());
        e.getChannel().close();
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

    private final static class NettyWriter implements AsyncIOWriter {

        private final Channel channel;
        private final HeapChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();
        private final ChannelBufferOutputStream os;

        public NettyWriter(Channel channel) {
            this.channel = channel;
            os = new ChannelBufferOutputStream(ChannelBuffers.dynamicBuffer());
        }

        @Override
        public void redirect(String location) throws IOException {
            //To change body of implemented methods use File | Settings | File Templates.
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeError(int errorCode, String message) throws IOException {
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(errorCode));
            channel.write(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void write(String data) throws IOException {
            byte[] b = data.getBytes("ISO-8859-1");
            os.write(b, 0, b.length);
        }

        @Override
        public void write(byte[] data) throws IOException {
            os.write(data, 0, data.length);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            os.write(data, offset, length);
        }

        public void flush() {
            channel.write(os.buffer());
        }

        @Override
        public void close() throws IOException {
            channel.close().addListener(ChannelFutureListener.CLOSE);
        }

        void suspend() throws IOException {
            channel.close();
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
