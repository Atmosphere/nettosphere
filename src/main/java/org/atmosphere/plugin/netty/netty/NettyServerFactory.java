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
/**
 * NOTE: This code was inspired/duplicated from APL 2 project called https://github.com/devsprint/jersey-netty-container
 */
/**
 * Copyright (C) 2011 Gabriel Ciuloaica (gciuloaica@gmail.com)
 *
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 */
package org.atmosphere.plugin.netty.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

import org.jboss.netty.channel.ChannelPipelineFactory;

import com.sun.jersey.api.container.ContainerFactory;
import com.sun.jersey.api.core.ResourceConfig;

public final class NettyServerFactory {
	
	private NettyServerFactory(){
		
	}

	public static NettyServer create(final ResourceConfig resourceConfig,final URI baseUri) {
		final NettyAtmosphereHandler jerseyHandler = ContainerFactory.createContainer(
				NettyAtmosphereHandler.class, resourceConfig);

		return new NettyServer(getPipelineFactory(jerseyHandler),
				getLocalSocket(baseUri));
	}

	private static SocketAddress getLocalSocket(final URI baseUri) {
		return new InetSocketAddress(baseUri.getHost(), baseUri.getPort());
	}

	private static ChannelPipelineFactory getPipelineFactory(
			final NettyAtmosphereHandler jerseyHandler) {
		return new AtmosphereChannelPipelineFactory(jerseyHandler);

	}

}
