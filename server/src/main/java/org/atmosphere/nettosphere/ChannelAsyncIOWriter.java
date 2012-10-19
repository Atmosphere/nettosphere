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

import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AsyncIOWriterAdapter;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link AsyncIOWriter} that bridge Atmosphere output stream with Netty's Channel.
 */
public class ChannelAsyncIOWriter extends AtmosphereInterceptorWriter {
    private static final Logger logger = LoggerFactory.getLogger(ChannelAsyncIOWriter.class);

    private final Channel channel;
    private final AtomicInteger pendingWrite = new AtomicInteger();
    private final AtomicBoolean asyncClose = new AtomicBoolean(false);
    private final ML listener = new ML();
    private boolean resumeOnBroadcast = false;
    private boolean byteWritten = false;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private boolean headerWritten = false;
    private final static String END = Integer.toHexString(0);
    private final static byte[] CHUNK_DELIMITER = "\r\n".getBytes();
    private final static byte[] ENDCHUNK = (END + "\r\n\r\n").getBytes();
    private long lastWrite = 0;

    public ChannelAsyncIOWriter(Channel channel) {
        this.channel = channel;
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public boolean byteWritten() {
        return byteWritten;
    }

    public void resumeOnBroadcast(boolean resumeOnBroadcast) {
        this.resumeOnBroadcast = resumeOnBroadcast;
    }

    @Override
    public AsyncIOWriter writeError(AtmosphereResponse r, int errorCode, String message) throws IOException {
        if (!channel.isOpen()) {
            return this;
        }

        try {
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(errorCode));
            channel.write(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Throwable ex) {
            logger.debug("", ex);
        }
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, String data) throws IOException {
        byte[] b = data.getBytes("ISO-8859-1");
        write(r, b);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data) throws IOException {
        write(r,data, 0, data.length);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data, int offset, int length) throws IOException {

        if (channel.isOpen()) {
            pendingWrite.incrementAndGet();
            final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();

            ChannelBufferOutputStream c = new ChannelBufferOutputStream(buffer);

            if (headerWritten) {
                c.write(Integer.toHexString(length - offset).getBytes("UTF-8"));
                c.write(CHUNK_DELIMITER);
            }

            c.write(data, offset, length);
            if (headerWritten) {
                c.write(CHUNK_DELIMITER);
            }

            channel.write(c.buffer()).addListener(listener);
            byteWritten = true;
            lastWrite = System.currentTimeMillis();
        } else {
            logger.warn("Trying to write on a closed channel {}", channel);
        }
        headerWritten = true;
        return this;
    }

    public long lastTick() {
        return lastWrite == -1 ? System.currentTimeMillis() : lastWrite;
    }

    @Override
    public void close(AtmosphereResponse r) throws IOException {
        // Make sure we don't have bufferred bytes
        if (!byteWritten) {
            r.getOutputStream().flush();
        }

        asyncClose.set(true);
        if (pendingWrite.get() == 0 && channel.isOpen()) {
            _close();
        }
    }

    private final class ML implements ChannelFutureListener {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (channel.isOpen() && (!future.isSuccess() || (pendingWrite.decrementAndGet() == 0 && (resumeOnBroadcast || asyncClose.get())))) {
                _close();
            }
        }
    }

    void _close() {
        if (!isClosed.getAndSet(true)) {
            headerWritten = false;
            final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
            ChannelBufferOutputStream c = new ChannelBufferOutputStream(buffer);
            try {
                c.write(ENDCHUNK);
                channel.write(buffer).addListener(ChannelFutureListener.CLOSE);
            } catch (IOException e) {
                logger.trace("Close error", e);
            }
        }
    }
}
