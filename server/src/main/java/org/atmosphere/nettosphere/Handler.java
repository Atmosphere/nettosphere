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

import org.atmosphere.cpr.AtmosphereResource;

/**
 * A super simple Handler for managing web application. As simple as
 * <blockquote>
 *    Nettosphere server = new Nettosphere.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource(new Handler() {
                        void handle(AtmosphereResource r) {
                            r.getResponse().write("Hello World".write("from Nettosphere").flush();
                        }
                    })
                    .build())
                 .build();
    server.start();
 * </blockquote>
 */
public interface Handler {

    /**
     * Handle an {@link AtmosphereResource}, from which you can retrieve an {@link org.atmosphere.cpr.AtmosphereRequest}
     * and {@link }AtmosphereResponse}
     * @param r an {@link AtmosphereResource}
     */
    void handle(AtmosphereResource r);

}
