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

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import org.atmosphere.websocket.WebSocket;

import java.util.HashSet;
import java.util.Set;

import static org.atmosphere.nettosphere.BridgeRuntime.NETTY_41_PLUS;
import static org.atmosphere.nettosphere.HttpStaticFileServerHandler.ATTACHMENT;

/**
 * This class expose some runtime properties of the Netty implementation
 *
 * @author Jeanfrancois Arcand
 */
public class RuntimeEngine {

    private final BridgeRuntime runtime;
    private final ChannelGroup httpChannels;
    private final ChannelGroup websocketChannels;

    public RuntimeEngine(BridgeRuntime runtime) {
        this.runtime = runtime;
        this.httpChannels = runtime.httpChannels();
        this.websocketChannels = runtime.websocketChannels();
    }

    /**
     * Return the underlying {@link Channel}.
     *
     * @param id the unique {@link Channel} ID.
     * @return the underlying {@link Channel}.
     */
    public <U> Channel find(U id) {
        Channel c = null;
        if (NETTY_41_PLUS) {
            c = websocketChannels.find((ChannelId) id);
            if (c == null) {
                c = httpChannels.find((ChannelId) id);
            }
        } else {
            throw new UnsupportedOperationException("You need to use Netty 4.1+ to use this feature");
        }
        return c;
    }

    /**
     * Retrieve the associated {@link WebSocket} attached to the {@link Channel}
     *
     * @param id the unique {@link Channel} ID.
     * @return the associated {@link WebSocket} attached to the {@link Channel}
     */
    public <U> WebSocket findWebSocket(U id) {
        if (NETTY_41_PLUS) {
            Channel c = websocketChannels.find((ChannelId) id);
            if (c != null) {
                Object o = c.attr(ATTACHMENT).get();
                if (o != null && WebSocket.class.isAssignableFrom(o.getClass())) {
                    return WebSocket.class.cast(o);
                }
            }
        } else {
            throw new UnsupportedOperationException("You need to use Netty 4.1+ to use this feature");
        }
        return null;
    }

    /**
     * Return all connected {@link WebSocket}.
     *
     * @return all connected {@link WebSocket}.
     */
    public Set<WebSocket> findAllWebSockets() {
        Set<WebSocket> s = new HashSet<WebSocket>();
        for (Channel c : websocketChannels) {
            if (c != null) {
                Object o = c.attr(ATTACHMENT).get();
                if (o != null && WebSocket.class.isAssignableFrom(o.getClass())) {
                    s.add(WebSocket.class.cast(o));
                }
            }
        }
        return s;
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

}
