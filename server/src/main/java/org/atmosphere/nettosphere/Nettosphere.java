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

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.nettosphere.extra.FlashPolicyServerPipelineFactory;
import org.atmosphere.nettosphere.util.Version;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
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
    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup("atmosphere");
    private final ChannelPipelineFactory pipelineFactory;
    private final ServerBootstrap bootstrap;
    private final SocketAddress localSocket;
    private final BridgeRuntime runtime;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ServerBootstrap bootstrapFlashPolicy;
    private final SocketAddress localPolicySocket;

    private Nettosphere(Config config) {
        runtime = new BridgeRuntime(config);
        this.pipelineFactory = new NettyPipelineFactory(runtime);
        this.localSocket = new InetSocketAddress(config.host(), config.port());
        this.bootstrap = buildBootstrap();

        configureBootstrap(bootstrap, config);

        if (config.initParams().containsKey(FLASH_SUPPORT)) {
            this.bootstrapFlashPolicy = buildBootstrapFlashPolicy();
            localPolicySocket = new InetSocketAddress(843);
        } else {
            this.bootstrapFlashPolicy = null;
            localPolicySocket = null;
        }
    }

    private void configureBootstrap(ServerBootstrap bootstrap, Config config) {
        bootstrap.setOption("child.tcpNoDelay", config.socketNoTcpDelay());
        bootstrap.setOption("child.keepAlive", config.socketKeepAlive());
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
        final Channel serverChannel = bootstrap.bind(localSocket);
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
            runtime.destroy();
            final ChannelGroupFuture future = ALL_CHANNELS.close();
            future.awaitUninterruptibly();
            bootstrap.getFactory().releaseExternalResources();
            ALL_CHANNELS.clear();
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

    private ServerBootstrap buildBootstrap() {
        final ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(pipelineFactory);
        return bootstrap;
    }

    private ServerBootstrap buildBootstrapFlashPolicy() {
        // Configure the server.
        final ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new FlashPolicyServerPipelineFactory());
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
