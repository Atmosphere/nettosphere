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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.nettosphere.extra.FlashPolicyServerChannelInitializer;
import org.atmosphere.nettosphere.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Start Atmosphere on top of Netty. To configure Atmosphere, use the {@link Config}.  As simple as
 * <blockquote><pre>
 * <p/>
 * Config config = new Config.Builder()
 *  .port(port)
 *  .host("127.0.0.1")
 *  .initParam("foo", "bar")
 *  .resource("/", new AtmosphereHandlerAdapter() {
 * <p/>
 *    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
 *    }
 * <p/>
 * }).build();
 * <p/>
 * server = new Nettosphere.Builder().config(config).build();
 * </pre></blockquote>
 *
 * @author Jeanfrancois Arcand
 */
public final class Nettosphere {

    public final static String FLASH_SUPPORT = Nettosphere.class.getName() + ".enableFlash";
    private static final Logger logger = LoggerFactory.getLogger(Nettosphere.class);
    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup("atmosphere",
            ImmediateEventExecutor.INSTANCE);
    @SuppressWarnings("rawtypes")
    private final ChannelInitializer channelInitializer;
    private final ServerBootstrap bootstrap;
    private final SocketAddress localSocket;
    private final BridgeRuntime runtime;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ServerBootstrap bootstrapFlashPolicy;
    private final SocketAddress localPolicySocket;
    private final RuntimeEngine runtimeEngine;
    private MultithreadEventLoopGroup parentGroup;
    private MultithreadEventLoopGroup childGroup;

    private Nettosphere(Config config) {
        runtime = new BridgeRuntime(config);
        this.channelInitializer = new NettyChannelInitializer(runtime);
        this.localSocket = new InetSocketAddress(config.host(), config.port());
        this.bootstrap = buildBootstrap(config);

        if (config.initParams().containsKey(FLASH_SUPPORT)) {
            this.bootstrapFlashPolicy = buildBootstrapFlashPolicy(config);
            localPolicySocket = new InetSocketAddress(843);
        } else {
            configureBootstrap(bootstrap, config);
            this.bootstrapFlashPolicy = null;
            localPolicySocket = null;
        }
        runtimeEngine = new RuntimeEngine(runtime);
    }

    private void configureBootstrap(ServerBootstrap bootstrap, Config config) {
        bootstrap.childOption(ChannelOption.TCP_NODELAY, config.socketNoTcpDelay());
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, config.socketKeepAlive());
    }

    /**
     * Return the {@link org.atmosphere.cpr.AtmosphereFramework} instance
     *
     * @return the {@link AtmosphereFramework} instance
     */
    public AtmosphereFramework framework() {
        return runtime.framework();
    }

    /**
     * Start the server
     */
    public void start() {
        final Channel serverChannel = bootstrap.bind(localSocket).channel();
        ALL_CHANNELS.add(serverChannel);
        started.set(true);
        if (bootstrapFlashPolicy != null) {
            try {
                bootstrapFlashPolicy.bind(localPolicySocket);
                logger.info("NettoSphere Flash Support Started on port {}.", localPolicySocket);
            } catch (Exception ex) {
                logger.error("", ex);
            }
        }
        logger.info("NettoSphere {} Started.", Version.getRawVersion());
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                Nettosphere.this.stop();
            }
        });
    }

    /**
     * Stop the Server
     */
    public void stop() {
        if (started.getAndSet(false)) {
          try {
            runtime.destroy();
            final ChannelGroupFuture future = ALL_CHANNELS.close();
            future.awaitUninterruptibly();
            ALL_CHANNELS.clear();
            parentGroup.shutdownGracefully().sync();
            childGroup.shutdownGracefully().sync();
          } catch(Exception ex) {
            logger.error("stop threw", ex);
          }
        }
    }

    /**
     * Return true is the server was successfully started.
     *
     * @return true is the server was successfully started.
     */
    public boolean isStarted() {
        return started.get();
    }

    private ServerBootstrap buildBootstrap(Config config) {
        final ServerBootstrap bootstrap = new ServerBootstrap();
        parentGroup = config.epoll() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        childGroup = config.epoll() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        bootstrap
                .channel(config.epoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .group(parentGroup, childGroup);

        bootstrap.childHandler(channelInitializer);
        return bootstrap;
    }

    private ServerBootstrap buildBootstrapFlashPolicy(Config config) {
        final ServerBootstrap bootstrap = new ServerBootstrap();
        parentGroup = config.epoll() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        childGroup = config.epoll() ? new EpollEventLoopGroup() : new NioEventLoopGroup();

        bootstrap
                .channel(config.epoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .group(parentGroup, childGroup);

        // Set up the event pipeline factory.
        bootstrap.childHandler(new FlashPolicyServerChannelInitializer());
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

    /**
     * Return the {@link RuntimeEngine}
     *
     * @return the {@link RuntimeEngine}
     */
    public RuntimeEngine runtimeEngine() {
        return runtimeEngine;
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
