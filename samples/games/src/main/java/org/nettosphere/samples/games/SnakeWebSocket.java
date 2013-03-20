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
package org.nettosphere.samples.games;

import org.atmosphere.config.service.WebSocketHandlerService;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor;

import java.io.IOException;
import java.util.Iterator;

/**
 * Simple AtmosphereHandler that implement the logic to build a Chat application.
 *
 * @author Jeanfrancois Arcand
 */
@WebSocketHandlerService(path = "/snake")
public class SnakeWebSocket extends SnakeGame implements WebSocketHandler {

    @Override
    public void onTextMessage(WebSocket webSocket, String message) throws IOException {
        Snake snake = snake(webSocket);
        if ("west".equals(message)) {
            snake.setDirection(Direction.WEST);
        } else if ("north".equals(message)) {
            snake.setDirection(Direction.NORTH);
        } else if ("east".equals(message)) {
            snake.setDirection(Direction.EAST);
        } else if ("south".equals(message)) {
            snake.setDirection(Direction.SOUTH);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) throws IOException {
        int id = snakeIds.getAndIncrement();
        webSocket.resource().getRequest().setAttribute("id", id);
        Snake snake = new Snake(id, webSocket);

        webSocket.resource().getRequest().setAttribute("snake", snake);
        snakeBroadcaster.addSnake(snake);
        StringBuilder sb = new StringBuilder();
        for (Iterator<Snake> iterator = snakeBroadcaster.getSnakes().iterator();
             iterator.hasNext(); ) {
            snake = iterator.next();
            sb.append(String.format("{id: %d, color: '%s'}",
                    Integer.valueOf(snake.getId()), snake.getHexColor()));
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
        snakeBroadcaster.broadcast(String.format("{'type': 'join','data':[%s]}",
                sb.toString()));
    }

    @Override
    public void onClose(WebSocket webSocket) {
        snakeBroadcaster.removeSnake(snake(webSocket));
        snakeBroadcaster.broadcast(String.format("{'type': 'leave', 'id': %d}",
                ((Integer) webSocket.resource().getRequest().getAttribute("id"))));
    }

    @Override
    public void onError(WebSocket webSocket, WebSocketProcessor.WebSocketException t) {
    }

    @Override
    public void onByteMessage(WebSocket webSocket, byte[] data, int offset, int length) throws IOException {
    }

    private Snake snake(WebSocket webSocket) {
        return (Snake) webSocket.resource().getRequest().getAttribute("snake");
    }

}
