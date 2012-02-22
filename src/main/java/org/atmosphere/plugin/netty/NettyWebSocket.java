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

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.websocket.WebSocketAdapter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.io.IOException;

public class NettyWebSocket extends WebSocketAdapter {

    private final Channel channel;
    private final AtmosphereConfig config;
    private final ChannelBufferFactory factory = new HeapChannelBufferFactory();

    public NettyWebSocket(Channel channel, AtmosphereConfig config) {
        this.channel = channel;
        this.config = config;
    }

    @Override
    public void redirect(String location) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeError(int errorCode, String message) throws IOException {

    }

    @Override
    public void write(String data) throws IOException {
        channel.write(new TextWebSocketFrame(data));
    }

    @Override
    public void write(byte[] data) throws IOException {
        String s = config.getInitParameter(ApplicationConfig.WEBSOCKET_BLOB);
        if (s != null && Boolean.parseBoolean(s)) {
            ChannelBuffer c = factory.getBuffer(data.length);
            c.writeBytes(data);
            channel.write(new BinaryWebSocketFrame(c));
        } else {
            channel.write(new TextWebSocketFrame(new String(data, 0, data.length, "UTF-8")));
        }
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
                String s = config.getInitParameter(ApplicationConfig.WEBSOCKET_BLOB);
        if (s != null && Boolean.parseBoolean(s)) {
            ChannelBuffer c = factory.getBuffer(length - offset);
            c.writeBytes(data);
            channel.write(new BinaryWebSocketFrame(c));
        } else {
            channel.write(new TextWebSocketFrame(new String(data, offset, length, "UTF-8")));
        }
    }

    @Override
    public void close() throws IOException {
        channel.close().addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void flush() throws IOException {
    }
}
