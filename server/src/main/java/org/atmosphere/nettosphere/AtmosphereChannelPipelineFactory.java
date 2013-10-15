/*
 * Copyright 2013 Jeanfrancois Arcand
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

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

import javax.net.ssl.SSLEngine;

import static org.jboss.netty.channel.Channels.pipeline;

class AtmosphereChannelPipelineFactory implements
        ChannelPipelineFactory {

    private final transient BridgeRuntime bridgeRuntime;
    private final transient Config config;

    public AtmosphereChannelPipelineFactory(final BridgeRuntime bridgeRuntime) {
        this.bridgeRuntime = bridgeRuntime;
        config = bridgeRuntime.config();
    }

    /**
     * Retrieve the channel pipeline factory.
     *
     * @see org.jboss.netty.channel.ChannelPipelineFactory#getPipeline()
     */
    public ChannelPipeline getPipeline() {
        final ChannelPipeline pipeline = pipeline();

        if (config.sslContext() != null) {
            SSLEngine e = config.sslContext().createSSLEngine();
            config.sslContextListener().onPostCreate(e);
            pipeline.addLast("ssl", new SslHandler(e));
        }

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
        pipeline.addLast("encoder", new HttpResponseEncoder());

        if (config.supportChunking()) {
            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
        }

        for (ChannelUpstreamHandler h: config.channelUpstreamHandlers()) {
            pipeline.addLast(h.getClass().getName(), h);
        }

        pipeline.addLast(BridgeRuntime.class.getName(), bridgeRuntime);

        return pipeline;
    }

}
