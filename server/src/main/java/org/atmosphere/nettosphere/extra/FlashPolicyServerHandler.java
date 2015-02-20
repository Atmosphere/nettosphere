/*
 * Copyright 2010 Bruce Mitchener.
 *
 * Bruce Mitchener licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.atmosphere.nettosphere.extra;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.CharsetUtil;

/**
 * @author <a href="http://www.waywardmonkeys.com/">Bruce Mitchener</a>
 */
public class FlashPolicyServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final String NEWLINE = "\r\n";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object e) throws Exception {
//        Object msg = e.getMessage();
        ChannelFuture f = ctx.channel().write(this.getPolicyFileContents());
        f.addListener(ChannelFutureListener.CLOSE);
    }

    private ByteBuf getPolicyFileContents() throws Exception {
        return Unpooled.copiedBuffer(
            "<?xml version=\"1.0\"?>" + NEWLINE +
            "<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">" + NEWLINE +
            "" + NEWLINE +
            "<!-- Policy file for xmlsocket://socks.example.com -->" + NEWLINE +
            "<cross-domain-policy> " + NEWLINE +
            "" + NEWLINE +
            "   <!-- This is a master socket policy file -->" + NEWLINE +
            "   <!-- No other socket policies on the host will be permitted -->" + NEWLINE +
            "   <site-control permitted-cross-domain-policies=\"master-only\"/>" + NEWLINE +
            "" + NEWLINE +
            "   <!-- Instead of setting to-ports=\"*\", administrator's can use ranges and commas -->" + NEWLINE +
            "   <allow-access-from domain=\"*\" to-ports=\"8080\" />" + NEWLINE +
            "" + NEWLINE +
            "</cross-domain-policy>" + NEWLINE,
            CharsetUtil.US_ASCII);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable t)
            throws Exception {
        if (t.getCause() instanceof ReadTimeoutException) {
            System.out.println("Connection timed out.");
            ctx.channel().close();
        } else {
            t.getCause().printStackTrace();
            ctx.channel().close();
        }
    }

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		channelRead(ctx, msg);
	}
}
