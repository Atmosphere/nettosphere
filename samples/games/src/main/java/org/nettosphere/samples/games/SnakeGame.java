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

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterFactory;

import java.awt.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class SnakeGame {
    public static final int PLAYFIELD_WIDTH = 640;
    public static final int PLAYFIELD_HEIGHT = 480;
    public static final int GRID_SIZE = 10;

    protected static final AtomicInteger snakeIds = new AtomicInteger(0);
    protected static final Random random = new Random();
    protected final SnakeBroadcaster snakeBroadcaster;

    public SnakeGame() {
        snakeBroadcaster = new SnakeBroadcaster(BroadcasterFactory.getDefault().lookup("/snake", true));
    }

    public static String getRandomHexColor() {
        float hue = random.nextFloat();
        // sat between 0.1 and 0.3
        float saturation = (random.nextInt(2000) + 1000) / 10000f;
        float luminance = 0.9f;
        Color color = Color.getHSBColor(hue, saturation, luminance);
        return '#' + Integer.toHexString(
                (color.getRGB() & 0xffffff) | 0x1000000).substring(1);
    }

    public static Location getRandomLocation() {
        int x = roundByGridSize(random.nextInt(PLAYFIELD_WIDTH));
        int y = roundByGridSize(random.nextInt(PLAYFIELD_HEIGHT));
        return new Location(x, y);
    }

    private static int roundByGridSize(int value) {
        value = value + (GRID_SIZE / 2);
        value = value / GRID_SIZE;
        value = value * GRID_SIZE;
        return value;
    }

    public void onOpen(AtmosphereResource resource) throws IOException {
        int id = snakeIds.getAndIncrement();
        resource.session().setAttribute("id", id);
        Snake snake = new Snake(id, resource);

        resource.session().setAttribute("snake", snake);
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

    public void onClose(AtmosphereResource resource) {
        snakeBroadcaster.removeSnake(snake(resource));
        snakeBroadcaster.broadcast(String.format("{'type': 'leave', 'id': %d}",
                ((Integer) resource.session().getAttribute("id"))));
    }

    protected Snake snake(AtmosphereResource resource) {
        return (Snake) resource.session().getAttribute("snake");
    }

    protected void onMessage(AtmosphereResource resource, String message) {
        Snake snake = snake(resource);
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
}
