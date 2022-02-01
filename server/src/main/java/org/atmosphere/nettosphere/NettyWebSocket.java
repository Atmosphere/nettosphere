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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.nettosphere.util.Utils;
import org.atmosphere.util.IOUtils;
import org.atmosphere.websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atmosphere.nettosphere.util.Utils.REMOTELY_CLOSED;

public class NettyWebSocket extends WebSocket {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocket.class);

    private final Channel channel;
    private final AtomicBoolean firstWrite = new AtomicBoolean(false);
    private boolean binaryWrite = false;
    private final boolean noInternalAlloc;
    private Future<?> closeFuture;
    private final AtomicBoolean isClosed = new AtomicBoolean();

    private io.netty.channel.ChannelId id;
    private NettyWebSocketFragmenter fragmenter;

    public NettyWebSocket(Channel channel, AtmosphereConfig config, boolean noInternalAlloc, boolean binaryWrite, int maxFrameSize) {
        super(config);
        this.channel = channel;

        this.fragmenter = new NettyWebSocketFragmenter(channel, maxFrameSize);

        this.noInternalAlloc = noInternalAlloc;
        this.binaryWrite = binaryWrite;
        this.lastWrite = System.currentTimeMillis();
    }

    public WebSocket resource(AtmosphereResource r) {
        super.resource(r);

        // TODO: Netty 4.1 id()
        if (noInternalAlloc) {
            this.uuid = BridgeRuntime.NETTY_41_PLUS ? channel.id().asLongText() : Utils.id(channel);
            if (BridgeRuntime.NETTY_41_PLUS) {
                id = channel.id();
            }
        }

        if (!binaryWrite && r != null && r.getRequest() != null) {
            try {
                binaryWrite = IOUtils.isBodyBinary(r.getRequest());
            } catch (Exception ex) {
                logger.trace("", ex);
                // Don't fail for any reason.
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(String data) throws IOException {
        return write(data.getBytes(StandardCharsets.UTF_8));
    }

    public WebSocket write(byte[] data) throws IOException {
        return write(data, 0, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(byte[] data, int offset, int length) throws IOException {
        firstWrite.set(true);

        if (!channel.isOpen()) throw REMOTELY_CLOSED;
        logger.trace("WebSocket.write() as binary {}", binaryWrite);

        fragmenter.write(data, offset, length, binaryWrite);
        lastWrite = System.currentTimeMillis();

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
        _close(new CloseWebSocketFrame());
    }

    @Override
    public void close(int statusCode, String reasonText) {
        _close(new CloseWebSocketFrame(statusCode, reasonText));
    }

    private void  _close(CloseWebSocketFrame closeFrame) {
        if (isClosed.getAndSet(true)) return;

        channel.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);

        if (closeFuture != null) {
            closeFuture.cancel(true);
        }
    }

    /**
     * Send a WebSocket Ping
     *
     * @param payload the bytes to send
     * @return this
     */
    public WebSocket sendPing(byte[] payload) {
        channel.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(payload)));
        return this;
    }

    /**
     * Send a WebSocket Pong
     *
     * @param payload the bytes to send
     * @return this
     */
    public WebSocket sendPong(byte[] payload) {
        channel.writeAndFlush(new PongWebSocketFrame(Unpooled.wrappedBuffer(payload)));
        return this;
    }

    /**
     * The remote ip address.
     *
     * @return The remote ip address.
     */
    public String address() {
        return ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
    }

    protected WebSocket closeFuture(Future<?> closeFuture) {
        this.closeFuture = closeFuture;
        return this;
    }

    protected Future<?> closeFuture() {
        return closeFuture;
    }

    public io.netty.channel.ChannelId id() {
        return id;
    }
}
