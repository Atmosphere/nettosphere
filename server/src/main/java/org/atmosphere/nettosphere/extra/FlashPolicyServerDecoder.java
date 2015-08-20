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

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

/**
 * @author <a href="http://www.waywardmonkeys.com/">Bruce Mitchener</a>
 */
public class FlashPolicyServerDecoder extends ReplayingDecoder<Void> {
    // We don't check for the trailing NULL to make telnet-based debugging easier.
    private final ByteBuf requestBuffer = Unpooled.copiedBuffer("<policy-file-request/>", CharsetUtil.US_ASCII);

    @Override
    protected void decode(
            ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {

    	ByteBuf data = buffer.readBytes(requestBuffer.readableBytes());
        if (data.equals(requestBuffer)) {
            return;
        }
        ctx.close();
    }
}
