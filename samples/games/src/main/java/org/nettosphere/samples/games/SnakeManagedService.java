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

import org.atmosphere.config.service.Disconnect;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Post;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceFactory;
import org.atmosphere.cpr.HeaderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

@ManagedService(path = "/snake")
public class SnakeManagedService extends SnakeGame {

    private Logger logger = LoggerFactory.getLogger(SnakeManagedService.class);
    private final ConcurrentLinkedQueue<String> uuids = new ConcurrentLinkedQueue<String>();

    @Ready
    public void onReady(final AtmosphereResource r) {
        if (!uuids.contains(r.uuid())) {
            try {
                super.onOpen(r);
            } catch (IOException e) {
                logger.error("", e);
            }
            uuids.add(r.uuid());
        }
    }

    @Disconnect
    public void onDisconnect(AtmosphereResourceEvent event) {
        AtmosphereRequest request = event.getResource().getRequest();
        String s = request.getHeader(HeaderConfig.X_ATMOSPHERE_TRANSPORT);
        if (s != null && s.equalsIgnoreCase(HeaderConfig.DISCONNECT)) {
            SnakeManagedService.super.onClose(event.getResource());
            uuids.remove(event.getResource().uuid());
        }
    }


    @Post
    public void onMessage(AtmosphereResource resource) {
        try {
            // Here we need to find the suspended AtmosphereResource
            super.onMessage(AtmosphereResourceFactory.getDefault().find(resource.uuid()), resource.getRequest().getReader().readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
