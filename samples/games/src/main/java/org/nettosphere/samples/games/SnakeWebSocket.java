/*
 * Copyright 2013 Jeanfrancois Arcand
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
package org.nettosphere.samples.games;

import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor;

import java.io.IOException;

/**
 * Simple WebSocketHandlerService that implement the logic to build a Snake Application.
 * The Game's code itself is taken from Apache Tomcat.
 *
 * @author Jeanfrancois Arcand
 */
// Uncomment if you want to only support WebSocket.
//@WebSocketHandlerService(path = "/snake")
public class SnakeWebSocket extends SnakeGame implements WebSocketHandler {

    @Override
    public void onTextMessage(WebSocket webSocket, String message) throws IOException {
        onMessage(webSocket.resource(), message);
    }

    @Override
    public void onOpen(WebSocket webSocket) throws IOException {
        super.onOpen(webSocket.resource());
    }

    @Override
    public void onClose(WebSocket webSocket) {
        super.onClose(webSocket.resource());
    }

    @Override
    public void onError(WebSocket webSocket, WebSocketProcessor.WebSocketException t) {
    }

    @Override
    public void onByteMessage(WebSocket webSocket, byte[] data, int offset, int length) throws IOException {
    }


}
