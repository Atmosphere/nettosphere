/**
 * Copyright 2008-2019 Async-IO.org
 */
package org.atmosphere.nettosphere;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;

public interface IOExceptionHandler {

    /**
     * Return true if the build-in {@link BridgeRuntime} exception mechanism must be invoked.
     * @param ctx {@link ChannelHandlerContext}
     * @param e {@link ExceptionEvent}
     * @return true if the build-in {@link BridgeRuntime} exception mechanism must be invoked.
     */
    boolean of(ChannelHandlerContext ctx, ExceptionEvent e);
}
