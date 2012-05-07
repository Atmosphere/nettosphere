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

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.websocket.WebSocket;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NettyWebSocket extends WebSocket {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocket.class);
    private final Channel channel;
    private final AtmosphereConfig config;
    private final ChannelBufferFactory factory = new HeapChannelBufferFactory();
    private final AtomicBoolean firstWrite = new AtomicBoolean(false);

    public NettyWebSocket(Channel channel, AtmosphereConfig config) {
        this.channel = channel;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket redirect(String location) throws IOException {
        logger.error("redirect not supported");
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket writeError(int errorCode, String message) throws IOException {
       if (!firstWrite.get()) {
            logger.debug("The WebSocket handshake succeeded but the dispatched URI failed {}:{}. " +
                    "The WebSocket connection is still open and client can continue sending messages.", message, errorCode);
        } else {
            logger.debug("{} {}", errorCode, message);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(String data) throws IOException {
        firstWrite.set(true);
        if (channel.isOpen()) {
            channel.write(new TextWebSocketFrame(data));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(byte[] data) throws IOException {
        firstWrite.set(true);

        if (channel.isOpen()) {
            String s = config.getInitParameter(ApplicationConfig.WEBSOCKET_BINARY_WRITE);
            if (s != null && Boolean.parseBoolean(s)) {
                ChannelBuffer c = factory.getBuffer(data.length);
                c.writeBytes(data);
                channel.write(new BinaryWebSocketFrame(c));
            } else {
                channel.write(new TextWebSocketFrame(new String(data, 0, data.length, "UTF-8")));
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(byte[] data, int offset, int length) throws IOException {
        firstWrite.set(true);

        if (channel.isOpen()) {
            String s = config.getInitParameter(ApplicationConfig.WEBSOCKET_BINARY_WRITE);
            if (s != null && Boolean.parseBoolean(s)) {
                ChannelBuffer c = factory.getBuffer(length - offset);
                c.writeBytes(data);
                channel.write(new BinaryWebSocketFrame(c));
            } else {
                channel.write(new TextWebSocketFrame(new String(data, offset, length, "UTF-8")));
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        channel.close().addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket flush() throws IOException {
        return this;
    }
}
