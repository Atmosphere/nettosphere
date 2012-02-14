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

public final class NettyServer {

	private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
	private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup("atmosphere");
	private final ChannelPipelineFactory pipelineFactory;
	private final ServerBootstrap bootstrap;
	private final SocketAddress localSocket;

	public NettyServer(Config config) {
		this.pipelineFactory = new AtmosphereChannelPipelineFactory(new NettyAtmosphereHandler(config));
		this.localSocket = new InetSocketAddress(config.host(), config.port());
		this.bootstrap = buildBootstrap();
	}

	public void startServer() {
		logger.info("Starting server....");
		final Channel serverChannel = bootstrap.bind(localSocket);
		ALL_CHANNELS.add(serverChannel);
	}

	public void stopServer() {
		logger.info("Stopping server....");
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


    public static void main(String[] args) throws Exception{
        Config.Builder b = new Config.Builder();
        b.path(args[0]).port(8080).host("127.0.0.1");
        NettyServer s = new NettyServer(b.build());
        s.startServer();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        br.readLine();
    }

}
