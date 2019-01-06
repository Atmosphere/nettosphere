/*
 * Copyright 2008-2019 Async-IO.org
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
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.nettosphere.util.Utils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A streaming {@link ChannelWriter} that write unchuncked bytes.
 */
public class StreamWriter extends ChannelWriter {
    private static final Logger logger = LoggerFactory.getLogger(StreamWriter.class);
    private ChannelBuffer chainedBodyBuffer;

    public StreamWriter(Channel channel, boolean writeHeader, boolean keepAlive) {
        super(channel, writeHeader, keepAlive);
        setUpBuffers();
    }

    @Override
    public AsyncIOWriter asyncWrite(AtmosphereResponse response, byte[] data, int offset, int length) throws IOException {
        chainedBodyBuffer = ChannelBuffers.wrappedBuffer(chainedBodyBuffer, ChannelBuffers.wrappedBuffer(data, offset, length));
        lastWrite = System.currentTimeMillis();
        return this;
    }

    private void setUpBuffers() {
        if (chainedBodyBuffer == null) {
            chainedBodyBuffer = ChannelBuffers.EMPTY_BUFFER;
        }
    }

    @Override
    public void close(AtmosphereResponse response) throws IOException {
        // Once we initiate the flush, we discard anything coming after for whatever reason.
        if (!doneProcessing.getAndSet(true) && channel.isOpen()) {
            logger.trace("About to flush to {} for {}", channel, response.uuid());

            ChannelBuffer statusAndHeadersBuffer = writeHeader ?
                    ChannelBuffers.wrappedBuffer(constructStatusAndHeaders(response, chainedBodyBuffer.readableBytes()).getBytes("UTF-8")) : ChannelBuffers.EMPTY_BUFFER;
            ChannelBuffer drain = ChannelBuffers.wrappedBuffer(statusAndHeadersBuffer, chainedBodyBuffer);
            channel.write(drain).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    chainedBodyBuffer = null;
                    if (!keepAlive) {
                        channel.close().awaitUninterruptibly();
                    }
                }
            });
        } else {
            throw Utils.ioExceptionForChannel(channel, response.uuid());
        }
    }

}
