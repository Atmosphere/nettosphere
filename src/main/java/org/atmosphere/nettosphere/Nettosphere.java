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
package org.atmosphere.nettosphere;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start Atmosphere on top of Netty. To configure Atmosphere, use the {@link Config}.
 */
public final class Nettosphere {

    private static final Logger logger = LoggerFactory.getLogger(Nettosphere.class);
    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup("atmosphere");
    private final ChannelPipelineFactory pipelineFactory;
    private final ServerBootstrap bootstrap;
    private final SocketAddress localSocket;
    private final NettyAtmosphereHandler handler;

    private Nettosphere(Config config) {
        handler = new NettyAtmosphereHandler(config);
        this.pipelineFactory = new AtmosphereChannelPipelineFactory(handler);
        this.localSocket = new InetSocketAddress(config.host(), config.port());
        this.bootstrap = buildBootstrap();
    }

    /**
     * Start the server
     */
    public void start() {
        final Channel serverChannel = bootstrap.bind(localSocket);
        ALL_CHANNELS.add(serverChannel);
    }

    /**
     * Stop the Server
     */
    public void stop() {
        handler.destroy();
        final ChannelGroupFuture future = ALL_CHANNELS.close();
        future.awaitUninterruptibly();
        bootstrap.getFactory().releaseExternalResources();
        ALL_CHANNELS.clear();
    }

    private ServerBootstrap buildBootstrap() {
        final ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(pipelineFactory);
        return bootstrap;
    }

    /**
     * Construct a {@link Nettosphere}.
     */
    public final static class Builder {

        private Config config = new Config.Builder().build();

        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        public Nettosphere build() {
            return new Nettosphere(config);
        }

    }

    public static void main(String[] args) throws Exception {
        Config.Builder b = new Config.Builder();
        b.resource(args[0]).port(8080).host("127.0.0.1");
        Nettosphere s = new Nettosphere(b.build());
        s.start();
        String a = "";

        logger.info("NettoSphere Server started");
        logger.info("Type quit to stop the server");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (!(a.equals("quit"))) {
            a = br.readLine();
        }
        System.exit(-1);
    }

}
