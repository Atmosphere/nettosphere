/*
 * Copyright 2017 Async-IO.org
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
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.nettosphere.util.ChannelBufferPool;
import org.atmosphere.nettosphere.util.Utils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An chunk based {@link ChannelWriter}
 */
public class ChunkedWriter extends ChannelWriter {
    private static final Logger logger = LoggerFactory.getLogger(ChunkedWriter.class);
    private final ChannelBufferPool channelBufferPool;
    private final ChannelBuffer END = ChannelBuffers.wrappedBuffer(ENDCHUNK);
    private final ChannelBuffer DELIMITER = ChannelBuffers.wrappedBuffer(CHUNK_DELIMITER);
    private final AtomicBoolean headerWritten = new AtomicBoolean();
    // We need a lock here to prevent two threads from competing to execute the write and the close operation concurrently.
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ChunkedWriter(Channel channel, boolean writeHeader, boolean keepAlive, ChannelBufferPool channelBufferPool) {
        super(channel, writeHeader, keepAlive);
        this.channelBufferPool = channelBufferPool;
    }

    private ChannelBuffer writeHeaders(AtmosphereResponse response) throws UnsupportedEncodingException {
        if (writeHeader && !headerWritten.getAndSet(true) && response != null) {
            ChannelBuffer writeBuffer = channelBufferPool.poll();
            return ChannelBuffers.wrappedBuffer(writeBuffer, ChannelBuffers.wrappedBuffer(constructStatusAndHeaders(response, -1).getBytes("UTF-8")));
        }
        return ChannelBuffers.EMPTY_BUFFER;
    }

    @Override
    public void close(final AtmosphereResponse response) throws IOException {
        if (!channel.isOpen() || doneProcessing.get()) return;

        ChannelBuffer writeBuffer = writeHeaders(response);
        if (writeBuffer.readableBytes() > 0 && response != null) {
            final AtomicReference<ChannelBuffer> recycle = new AtomicReference<ChannelBuffer>(writeBuffer);
            try {
                lock.writeLock().lock();
                channel.write(writeBuffer).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        channelBufferPool.offer(recycle.get());
                        prepareForClose(response);
                    }
                });
            } finally {
                lock.writeLock().unlock();
            }
        } else {
            try {
                lock.writeLock().lock();
                prepareForClose(response);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    void prepareForClose(final AtmosphereResponse response) throws UnsupportedEncodingException {
        AtmosphereResource r = response != null ? response.resource() : null;
        if (r == null || r.isSuspended() && !r.isResumed()) {
            keepAlive = false;
        }

        _close(response);
    }

    void _close(AtmosphereResponse response) throws UnsupportedEncodingException {
        if (!doneProcessing.getAndSet(true) && channel.isOpen()) {
            ChannelBuffer writeBuffer = writeHeaders(response);

            writeBuffer = ChannelBuffers.wrappedBuffer(writeBuffer, END);
            channel.write(writeBuffer).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    logger.trace("Async Closing Done {}", channel);
                    if (!keepAlive) {
                        channel.close().awaitUninterruptibly();
                    }
                }
            });
        }
    }

    @Override
    public AsyncIOWriter asyncWrite(final AtmosphereResponse response, byte[] data, int offset, int length) throws IOException {

            // Client will close the connection if we don't reject empty bytes.
            if (length == 0) {
                logger.trace("Data is empty {} => {}", data, length);
                return this;
            }

            ChannelBuffer writeBuffer = writeHeaders(response);
            if (headerWritten.get()) {
                writeBuffer = ChannelBuffers.wrappedBuffer(writeBuffer, ChannelBuffers.wrappedBuffer(Integer.toHexString(length - offset).getBytes("UTF-8")));
                writeBuffer = ChannelBuffers.wrappedBuffer(writeBuffer, DELIMITER);
            }

            writeBuffer = ChannelBuffers.wrappedBuffer(writeBuffer, ChannelBuffers.wrappedBuffer(data, offset, length));
            if (headerWritten.get()) {
                writeBuffer = ChannelBuffers.wrappedBuffer(writeBuffer, DELIMITER);
            }

            final AtomicReference<ChannelBuffer> recycle = new AtomicReference<ChannelBuffer>(writeBuffer);

            try {
                lock.writeLock().lock();

                // We got closed, so we throw an IOException so the message get cached.
                if (doneProcessing.get() && !response.resource().getAtmosphereConfig().framework().isDestroyed()){
                    throw Utils.ioExceptionForChannel(channel, response.uuid());
                }

                channel.write(writeBuffer).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        channelBufferPool.offer(recycle.get());
                        if (channel.isOpen() && !future.isSuccess()) {
                            _close(response);
                        }
                    }
                });
            } finally {
                lock.writeLock().unlock();
            }
            lastWrite = System.currentTimeMillis();
            return this;
    }
}
