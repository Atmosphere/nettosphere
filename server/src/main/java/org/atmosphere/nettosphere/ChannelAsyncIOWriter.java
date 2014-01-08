/*
 * Copyright 2014 Jeanfrancois Arcand
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
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.util.ByteArrayAsyncWriter;
import org.jboss.netty.buffer.ChannelBuffer;
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
import java.util.Map;
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
    private boolean byteWritten = false;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private boolean headerWritten = false;
    private final static String END = Integer.toHexString(0);
    private final static byte[] CHUNK_DELIMITER = "\r\n".getBytes();
    private final static byte[] ENDCHUNK = (END + "\r\n\r\n").getBytes();
    private long lastWrite = 0;
    private final ByteArrayAsyncWriter buffer = new ByteArrayAsyncWriter();
    private final boolean writeHeader;
    private boolean keepAlive;

    public ChannelAsyncIOWriter(Channel channel, boolean writeHeader, boolean keepAlive) {
        this.channel = channel;
        this.writeHeader = writeHeader;
        this.keepAlive = keepAlive;
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public boolean byteWritten() {
        return byteWritten;
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
        write(r, data, 0, data.length);
        return this;
    }

    protected byte[] transform(AtmosphereResponse response, byte[] b, int offset, int length) throws IOException {
        AsyncIOWriter a = response.getAsyncIOWriter();
        try {
            response.asyncIOWriter(buffer);
            invokeInterceptor(response, b, offset, length);
            return buffer.stream().toByteArray();
        } finally {
            buffer.close(null);
            response.asyncIOWriter(a);
        }
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data, int offset, int length) throws IOException {

        boolean transform = filters.size() > 0 && r.getStatus() < 400;
        if (transform) {
            data = transform(r, data, offset, length);
            offset = 0;
            length = data.length;
        }

        logger.trace("About to write to {}", r.resource() != null ? r.resource().uuid() : "null");
        if (channel.isOpen()) {
            final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
            pendingWrite.incrementAndGet();
            if (writeHeader && !headerWritten) {
                buffer.writeBytes(constructStatusAndHeaders(r).getBytes("UTF-8"));
                headerWritten = true;
            }

            if (headerWritten) {
                buffer.writeBytes(Integer.toHexString(length - offset).getBytes("UTF-8"));
                buffer.writeBytes(CHUNK_DELIMITER);
            }

            buffer.writeBytes(data, offset, length);
            if (headerWritten) {
                buffer.writeBytes(CHUNK_DELIMITER);
            }

            channel.write(buffer).addListener(listener);
            byteWritten = true;
            lastWrite = System.currentTimeMillis();
        } else {
            logger.debug("Trying to write on a closed channel {}", channel);
            throw new IOException("Channel closed");
        }
        headerWritten = true;
        return this;
    }

    public long lastTick() {
        return lastWrite == -1 ? System.currentTimeMillis() : lastWrite;
    }

    @Override
    public void close(AtmosphereResponse response) throws IOException {

        if (!channel.isOpen()) return;

        if (writeHeader && !headerWritten && response != null) {
            final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();

            buffer.writeBytes(constructStatusAndHeaders(response).getBytes("UTF-8"));
            channel.write(buffer);
            headerWritten = true;
        }

        // Make sure we don't have bufferred bytes
        if (!byteWritten && response != null && response.getOutputStream() != null) {
            response.getOutputStream().flush();
        }

        asyncClose.set(true);

        AtmosphereResource r = response != null ? response.resource() : null;

        if (r == null || r.isSuspended() && !r.isResumed()) {
            keepAlive = false;
        }

        if (pendingWrite.get() == 0 && channel.isOpen()) {
            _close();
        }
    }

    private final class ML implements ChannelFutureListener {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (channel.isOpen() && (!future.isSuccess() || (pendingWrite.decrementAndGet() == 0 && asyncClose.get()))) {
                _close();
            }
        }
    }

    void _close() {
        if (!isClosed.getAndSet(true)) {
            headerWritten = false;
            final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
            buffer.writeBytes(ENDCHUNK);
            channel.write(buffer).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!keepAlive) {
                        channel.close().awaitUninterruptibly();
                    }
                }
            });
        }
    }

    // Duplicate from AtmosphereResource.constructStatusAndHeaders
    private String constructStatusAndHeaders(AtmosphereResponse response) {
        StringBuffer b = new StringBuffer("HTTP/1.1")
                .append(" ")
                .append(response.getStatus())
                .append(" ")
                .append(response.getStatusMessage())
                .append("\n");

        Map<String, String> headers = response.headers();
        String contentType = response.getContentType();
        int contentLength = -1; //FIX ME

        b.append("Content-Type").append(":").append(headers.get("Content-Type") == null ? contentType : headers.get("Content-Type")).append("\n");
        if (contentLength != -1) {
            b.append("Content-Length").append(":").append(contentLength).append("\n");
        }

        for (String s : headers.keySet()) {
            if (!s.equalsIgnoreCase("Content-Type")) {
                b.append(s).append(":").append(headers.get(s)).append("\n");
            }
        }
        b.deleteCharAt(b.length() - 1);
        b.append("\r\n\r\n");
        return b.toString();
    }
}
