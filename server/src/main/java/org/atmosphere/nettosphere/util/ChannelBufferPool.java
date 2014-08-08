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
package org.atmosphere.nettosphere.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.util.ExecutorsFactory;

public class ChannelBufferPool {
    private final ConcurrentLinkedQueue<ByteBuf> pool = new ConcurrentLinkedQueue<ByteBuf>();
    private int writeBufferPoolSize = 50;

    public ChannelBufferPool(final int minIdle) {
        initialize(minIdle);
    }

    public ChannelBufferPool(final int minIdle, final int writeBufferPoolSize, final long validationInterval, AtmosphereConfig config) {
        this.writeBufferPoolSize = writeBufferPoolSize;

        initialize(minIdle);

        if (writeBufferPoolSize != -1) {
            ExecutorsFactory.getScheduler(config).scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    int size = pool.size();
                    if (size < minIdle) {
                        int sizeToBeAdded = minIdle - size;
                        for (int i = 0; i < sizeToBeAdded; i++) {
                            pool.add(createObject());
                        }
                    } else if (size > writeBufferPoolSize) {
                        int sizeToBeRemoved = size - writeBufferPoolSize;
                        for (int i = 0; i < sizeToBeRemoved; i++) {
                            pool.poll();
                        }
                    }
                }
            }, validationInterval, validationInterval, TimeUnit.SECONDS);

            config.shutdownHook(new AtmosphereConfig.ShutdownHook() {
                @Override
                public void shutdown() {
                    pool.clear();
                }
            });
        }
    }

    public ByteBuf poll() {
    	ByteBuf channelBuffer;
        if ((channelBuffer = pool.poll()) == null) {
            channelBuffer = createObject();
        }

        return channelBuffer;
    }

    public void offer(ByteBuf channelBuffer) {
        if (channelBuffer == null || writeBufferPoolSize == -1) {
            return;
        }

        channelBuffer.clear();
        this.pool.offer(channelBuffer);
    }

    protected ByteBuf createObject() {
        return Unpooled.buffer();
    }

    private void initialize(final int minIdle) {
        for (int i = 0; i < minIdle; i++) {
            pool.add(createObject());
        }
    }
}