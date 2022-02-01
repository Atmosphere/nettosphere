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

import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.nettosphere.util.Utils;
import org.atmosphere.util.ByteArrayAsyncWriter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ChannelWriter extends AtmosphereInterceptorWriter {
    protected final static Logger logger = LoggerFactory.getLogger(StreamWriter.class);
    protected final static String END = Integer.toHexString(0);
    protected static final String CRLF = "\r\n";
    protected final static byte[] CHUNK_DELIMITER = CRLF.getBytes();
    protected final static byte[] ENDCHUNK = (END + CRLF + CRLF).getBytes();

    protected final Channel channel;
    protected final AtomicBoolean doneProcessing = new AtomicBoolean(false);
    protected final ByteArrayAsyncWriter transformCacheBuffer = new ByteArrayAsyncWriter();
    protected final boolean writeHeader;

    protected long lastWrite = 0;
    protected boolean keepAlive;

    public ChannelWriter(Channel channel, boolean writeHeader, boolean keepAlive) {
        this.channel = channel;
        this.writeHeader = writeHeader;
        this.keepAlive = keepAlive;
    }

    public boolean isClosed() {
        return doneProcessing.get();
    }

    @Override
    public AsyncIOWriter writeError(AtmosphereResponse response, int errorCode, String message) throws IOException {
        if (!channel.isOpen()) {
            return this;
        }

        try {
            DefaultHttpResponse r = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(errorCode));
            channel.write(r).addListener(ChannelFutureListener.CLOSE);
        } catch (Throwable ex) {
            logger.debug("", ex);
        }
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse response, String data) throws IOException {
        byte[] b = data.getBytes("ISO-8859-1");
        write(response, b);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse response, byte[] data) throws IOException {
        write(response, data, 0, data.length);
        return this;
    }

    protected byte[] transform(AtmosphereResponse response, byte[] b, int offset, int length) throws IOException {
        AsyncIOWriter a = response.getAsyncIOWriter();
        try {
            response.asyncIOWriter(transformCacheBuffer);
            invokeInterceptor(response, b, offset, length);
            return transformCacheBuffer.stream().toByteArray();
        } finally {
            transformCacheBuffer.close(null);
            response.asyncIOWriter(a);
        }
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse response, byte[] data, int offset, int length) throws IOException {

        if (doneProcessing.get()) {
            throw Utils.ioExceptionForChannel(channel, response.uuid());
        }

        boolean transform = filters.size() > 0 && response.getStatus() < 400;
        if (transform) {
            data = transform(response, data, offset, length);
            offset = 0;
            length = data.length;
        }

        logger.trace("About to write to {}", channel);
        if (channel.isOpen()) {
            asyncWrite(response, data, offset, length);
        } else {
            logger.trace("Trying to write on a closed channel {}", channel);
            throw new IOException("Channel closed");
        }
        return this;
    }


    public long lastTick() {
        return lastWrite == -1 ? System.currentTimeMillis() : lastWrite;
    }

    protected String constructStatusAndHeaders(AtmosphereResponse response, int contentLength) {
        StringBuffer b = new StringBuffer("HTTP/1.1")
                .append(" ")
                .append(response.getStatus())
                .append(" ")
                .append(response.getStatusMessage())
                .append(CRLF);

        Map<String, String> headers = response.headers();
        String contentType = response.getContentType();

        b.append("Content-Type").append(":").append(headers.get("Content-Type") == null ? contentType : headers.get("Content-Type")).append(CRLF);
        if (contentLength != -1) {
            b.append("Content-Length").append(":").append(contentLength).append(CRLF);
        }

        for (String s : headers.keySet()) {
            if (!s.equalsIgnoreCase("Content-Type")) {
                b.append(s).append(":").append(headers.get(s)).append(CRLF);
            }
        }
        b.append(CRLF);
        return b.toString();
    }

    abstract public AsyncIOWriter asyncWrite(AtmosphereResponse response, byte[] data, int offset, int length) throws IOException;

}
