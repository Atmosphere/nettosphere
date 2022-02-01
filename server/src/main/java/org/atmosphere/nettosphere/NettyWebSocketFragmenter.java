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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

public class NettyWebSocketFragmenter {

    private final Channel channel;
    private final int maxFrameSize;

    public NettyWebSocketFragmenter(Channel channel, int maxFrameSize) {
        this.channel = channel;
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * From RFC 6455 <a href=https://tools.ietf.org/html/rfc6455>https://tools.ietf.org/html/rfc6455</a>
     * <p>
     * An unfragmented message consists of a single frame with the FIN bit set and an opcode other than 0.
     * A fragmented message consists of a single frame with the FIN bit clear and an opcode other than 0,
     * followed by zero or more frames with the FIN bit clear and the opcode set to 0, and terminated by
     * a single frame with the FIN bit set and an opcode of 0.
     * 0x0 denotes a continuation frame
     * 0x1 denotes a text frame
     * 0x2 denotes a binary frame
     */
    public void write(byte[] data, int offset, int length, boolean binary) {
        if (length <= maxFrameSize) {
            channel.writeAndFlush(getWebSocketFrameFinal(Unpooled.wrappedBuffer(data, offset, length), binary));
        } else {
            channel.write(getWebSocketFrameNotFinal(Unpooled.wrappedBuffer(data, offset, maxFrameSize), binary));
            int i = offset + maxFrameSize;
            int lengthMinusMaxFrameSize = length - maxFrameSize;
            for (; i < lengthMinusMaxFrameSize; i += maxFrameSize) {
                channel.write(new ContinuationWebSocketFrame(
                        false, 0, Unpooled.wrappedBuffer(data, i, maxFrameSize)));
            }
            channel.write(new ContinuationWebSocketFrame(
                    true, 0, Unpooled.wrappedBuffer(data, i, length - i)));
            channel.flush();
        }
    }

    private static WebSocketFrame getWebSocketFrameFinal(ByteBuf bb, boolean binary) {
        return binary ? new BinaryWebSocketFrame(bb) : new TextWebSocketFrame(bb);
    }

    private static WebSocketFrame getWebSocketFrameNotFinal(ByteBuf bb, boolean binary) {
        return binary ? new BinaryWebSocketFrame(false, 0, bb) :
                new TextWebSocketFrame(false, 0, bb);
    }
}
