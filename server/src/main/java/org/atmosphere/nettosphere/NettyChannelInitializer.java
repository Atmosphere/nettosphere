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

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import javax.net.ssl.SSLEngine;

class NettyChannelInitializer extends ChannelInitializer {

    private final transient BridgeRuntime bridgeRuntime;
    private final transient Config config;

    public NettyChannelInitializer(final BridgeRuntime bridgeRuntime) {
        this.bridgeRuntime = bridgeRuntime;
        config = bridgeRuntime.config();
    }

	@Override
	protected void initChannel(Channel ch) throws Exception {
        final ChannelPipeline pipeline = ch.pipeline();

        if (config.sslContext() != null) {
            SSLEngine e = config.sslContext().createSSLEngine();
            config.sslContextListener().onPostCreate(e);
            pipeline.addLast("ssl", new SslHandler(e));
        }

        pipeline.addLast("decoder", new HttpRequestDecoder());

        if (config.aggregateRequestBodyInMemory()) {
            pipeline.addLast("aggregator", new HttpObjectAggregator(config.maxChunkContentLength()));
        }

        pipeline.addLast("encoder", new HttpResponseEncoder());

        if (config.supportChunking()) {
            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
        }

        for (ChannelInboundHandler h: config.channelUpstreamHandlers()) {
            pipeline.addLast(h.getClass().getName(), h);
        }

        pipeline.addLast(BridgeRuntime.class.getName(), bridgeRuntime);

	}

}
