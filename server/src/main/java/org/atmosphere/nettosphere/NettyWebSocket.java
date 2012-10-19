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
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketResponseFilter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
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
    private final ChannelBufferFactory factory = new HeapChannelBufferFactory();
    private final AtomicBoolean firstWrite = new AtomicBoolean(false);

    public NettyWebSocket(Channel channel, AtmosphereConfig config) {
        super(config);
        this.channel = channel;
    }

    /**
     * {@inheritDoc}
     */
    public WebSocket write(String data) throws IOException {
        firstWrite.set(true);
        if (!channel.isOpen()) throw new IOException("Connection remotely closed");
        logger.trace("WebSocket.write()");

        if (binaryWrite) {
            channel.write(new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(data.getBytes("UTF-8"))));
        } else {
            channel.write(new TextWebSocketFrame(data));
        }
        lastWrite = System.currentTimeMillis();
        return this;
    }

    public WebSocket write(AtmosphereResponse r, byte[] data) throws IOException {
        firstWrite.set(true);
        if (!channel.isOpen()) throw new IOException("Connection remotely closed");

        logger.trace("WebSocket.write()");
        if (binaryWrite) {
            channel.write(new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(data)));
        } else {
            channel.write(new TextWebSocketFrame(new String(data, "UTF-8")));
        }
        lastWrite = System.currentTimeMillis();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(byte[] data, int offset, int length) throws IOException {
        firstWrite.set(true);

        if (channel.isOpen()) {
            String s = config().getInitParameter(ApplicationConfig.WEBSOCKET_BINARY_WRITE);
            if (s != null && Boolean.parseBoolean(s)) {
                ChannelBuffer c = factory.getBuffer(length - offset);
                c.writeBytes(data);
                channel.write(new BinaryWebSocketFrame(c));
            } else {
            }
        }
        return this;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        channel.close().addListener(ChannelFutureListener.CLOSE);
    }
}
