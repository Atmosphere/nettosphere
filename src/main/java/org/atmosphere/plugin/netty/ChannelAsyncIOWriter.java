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
public class ChannelAsyncIOWriter implements AsyncIOWriter {
    private static final Logger logger = LoggerFactory.getLogger(ChannelAsyncIOWriter.class);

    private final Channel channel;
    private final AtomicInteger pendingWrite = new AtomicInteger();
    private final AtomicBoolean asyncClose = new AtomicBoolean(false);
    private final ML listener = new ML();
    private boolean resumeOnBroadcast = false;
    private boolean byteWritten = false;

    public ChannelAsyncIOWriter(Channel channel) {
        this.channel = channel;
    }

    public boolean byteWritten() {
        return byteWritten;
    }

    public void resumeOnBroadcast(boolean resumeOnBroadcast) {
        this.resumeOnBroadcast = resumeOnBroadcast;
    }

    @Override
    public void redirect(String location) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeError(int errorCode, String message) throws IOException {
        if (!channel.isOpen()) {
            return;
        }

        try {
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(errorCode));
            channel.write(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Throwable ex) {
            logger.debug("", ex);
        }
    }

    @Override
    public void write(String data) throws IOException {
        byte[] b = data.getBytes("ISO-8859-1");
        write(b);
    }

    @Override
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        pendingWrite.incrementAndGet();
        final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();

        ChannelBufferOutputStream c = new ChannelBufferOutputStream(buffer);
        c.write(data, offset, length);
        channel.write(c.buffer()).addListener(listener);
        byteWritten = true;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws IOException {
        asyncClose.set(true);
        if (pendingWrite.get() == 0) {
            channel.close();
        }
    }

    private final class ML implements ChannelFutureListener {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess() || (pendingWrite.decrementAndGet() == 0 && (resumeOnBroadcast || asyncClose.get()))) {
                channel.close();
            }
        }
    }
}
