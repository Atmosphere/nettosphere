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
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nettosphere.samples.games;

import org.atmosphere.cpr.Broadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Sets up the timer for the multi-player snake game WebSocket example.
 */
public class SnakeBroadcaster {

    private final static Logger logger = LoggerFactory.getLogger(SnakeBroadcaster.class);

    private Timer gameTimer = null;

    private final long TICK_DELAY = 100;

    private final ConcurrentHashMap<Integer, Snake> snakes =
            new ConcurrentHashMap<Integer, Snake>();

    private final Broadcaster broadcaster;

    public SnakeBroadcaster(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    protected synchronized void addSnake(Snake snake) {
        if (snakes.size() == 0) {
            startTimer();
        }
        snakes.put(Integer.valueOf(snake.getId()), snake);
    }


    protected Collection<Snake> getSnakes() {
        return Collections.unmodifiableCollection(snakes.values());
    }


    protected synchronized void removeSnake(Snake snake) {
        snakes.remove(Integer.valueOf(snake.getId()));
    }


    protected String tick() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<Snake> iterator = getSnakes().iterator();
             iterator.hasNext(); ) {
            Snake snake = iterator.next();
            snake.update(getSnakes());
            sb.append(snake.getLocationsJson());
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
        return String.format("{'type': 'update', 'data' : [%s]}",
                sb.toString());
    }

    public SnakeBroadcaster broadcast(String message) {
        broadcaster.broadcast(message);
        return this;
    }

    public void startTimer() {
        broadcaster.scheduleFixedBroadcast(new Callable<String>() {
            @Override
            public String call() {
                try {
                    return tick();
                } catch (RuntimeException e) {
                    logger.error("Caught to prevent timer from shutting down", e);
                }
                return "";
            }
        }, TICK_DELAY, TICK_DELAY, TimeUnit.MILLISECONDS);
    }
}
