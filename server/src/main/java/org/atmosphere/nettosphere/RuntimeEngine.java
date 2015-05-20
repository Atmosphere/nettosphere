/*
 * Copyright 2015 Async-IO.org
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

import org.atmosphere.nettosphere.util.ChannelBufferPool;
import org.atmosphere.websocket.WebSocket;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroup;

/**
 * This class expose some runtime properties of the Netty implementation
 *
 * @author Jeanfrancois Arcand
 */
public class RuntimeEngine {

    private final BridgeRuntime runtime;

    public RuntimeEngine(BridgeRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Return the underlying {@link Channel}.
     *
     * @param id the unique {@link Channel} ID.
     * @return the underlying {@link Channel}.
     */
    public Channel find(int id) {
        Channel c = runtime.websocketChannels().find(id);
        if (c == null) {
            c = runtime.httpChannels().find(id);
        }
        return c;
    }

    /**
     * Retrieve the associated {@link WebSocket} attached to the {@link Channel}
     *
     * @param id the unique {@link Channel} ID.
     * @return the associated {@link WebSocket} attached to the {@link Channel}
     */
    public WebSocket findWebSocket(int id) {
        Channel c = runtime.websocketChannels().find(id);
        if (c != null) {
            Object o = c.getAttachment();
            if (o != null && WebSocket.class.isAssignableFrom(o.getClass())) {
                return WebSocket.class.cast(o);
            }
        }
        return null;
    }

    /**
     * Return the {@link ChannelGroup} associated with HTTP requests.
     *
     * @return the {@link ChannelGroup} associated with HTTP requests.
     */
    public ChannelGroup httpChannels() {
        return runtime.httpChannels();
    }

    /**
     * Return the {@link ChannelGroup} associated with websocket requests.
     *
     * @return the {@link ChannelGroup} associated with websocket requests.
     */
    public ChannelGroup websocketChannels() {
        return runtime.websocketChannels();
    }

    /**
     * The {@link ChannelBufferPool} when enabled.
     * @return {@link ChannelBufferPool} when enabled.
     */
    public ChannelBufferPool channelBufferPool() {
        return runtime.channelBufferPool();
    }
}
