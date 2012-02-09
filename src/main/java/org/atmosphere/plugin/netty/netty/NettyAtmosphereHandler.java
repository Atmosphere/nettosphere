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
/**
 * NOTE: This code was inspired/duplicated from APL 2 project called https://github.com/devsprint/jersey-netty-container
 */
package org.atmosphere.plugin.netty.netty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereServlet;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
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

import javax.servlet.ServletException;

class NettyAtmosphereHandler extends SimpleChannelUpstreamHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyAtmosphereHandler.class);

    public NettyAtmosphereHandler() {
        super();
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context,
                                final MessageEvent messageEvent) throws URISyntaxException,
            IOException {
        final HttpRequest request = (HttpRequest) messageEvent.getMessage();

        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.getUri());

        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        AtmosphereRequest r = requestBuilder.requestURI(requestUri.toURL().toString())
                .requestURL(requestUri.toURL().toString())
                .headers(getHeaders(request))
                .method(request.getMethod().getName())
                .contentType(request.getHeaders("Content-Type").get(0))
                .inputStream(new ChannelBufferInputStream(request.getContent()))
                .build();

        AtmosphereResponse.Builder responseBuilder = new AtmosphereResponse.Builder();
        responseBuilder.asyncIOWriter(new NettyWriter(context.getChannel()))
                .atmosphereRequest(r);

        try {
            new AtmosphereServlet().doCometSupport(r, responseBuilder.build());
        } catch (ServletException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        // Close the connection when an exception is raised.
        LOGGER.warn("Unexpected exception from downstream.", e.getCause());
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

        public NettyWriter(Channel channel) {
            this.channel = channel;
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
            channel.write(response);
        }

        @Override
        public void write(String data) throws IOException {
            channel.write(ByteBuffer.wrap(data.getBytes()));
        }

        @Override
        public void write(byte[] data) throws IOException {
            channel.write(ByteBuffer.wrap(data));
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            channel.write(ByteBuffer.wrap(data, offset, length));
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

}
