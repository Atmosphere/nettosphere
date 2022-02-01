/**
 * Copyright 2008-2022 Async-IO.org
 */
package org.atmosphere.nettosphere;

import io.netty.channel.ChannelHandlerContext;

public interface IOExceptionHandler {

    /**
     * Return true if the build-in {@link BridgeRuntime} exception mechanism must be invoked.
     * @param ctx {@link ChannelHandlerContext}
     * @param e {@link Throwable}
     * @return true if the build-in {@link BridgeRuntime} exception mechanism must be invoked.
     */
    boolean of(ChannelHandlerContext ctx, Throwable e);
}
