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

import org.atmosphere.config.service.Get;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Post;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.cpr.AtmosphereResourceFactory;
import org.atmosphere.cpr.HeaderConfig;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

@ManagedService(path = "/snake")
public class SnakeManagedService extends SnakeGame {

    private final ConcurrentLinkedQueue<String> uuids = new ConcurrentLinkedQueue<String>();

    @Get
    public void onOpen(final AtmosphereResource resource) {
        resource.addEventListener(new AtmosphereResourceEventListenerAdapter() {
            @Override
            public void onSuspend(AtmosphereResourceEvent event) {
                try {
                    if (!uuids.contains(resource.uuid())) {
                        SnakeManagedService.super.onOpen(resource);
                        uuids.add(resource.uuid());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onDisconnect(AtmosphereResourceEvent event) {
                AtmosphereRequest request = event.getResource().getRequest();
                String s = request.getHeader(HeaderConfig.X_ATMOSPHERE_TRANSPORT);
                if (s != null && s.equalsIgnoreCase(HeaderConfig.DISCONNECT)) {
                    SnakeManagedService.super.onClose(resource);
                    uuids.remove(resource.uuid());
                }
            }
        });
    }

    @Post
    public void onMessage(AtmosphereResource resource) {
        try {
            // Here we need to find the suspended AtmosphereResource
            SnakeManagedService.super.onMessage(AtmosphereResourceFactory.getDefault().find(resource.uuid()), resource.getRequest().getReader().readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
