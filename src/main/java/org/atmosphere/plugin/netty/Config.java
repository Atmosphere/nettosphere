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
package org.atmosphere.plugin.netty;

import java.util.HashMap;
import java.util.Map;

public class Config {

    private final Builder b;

    public Config(Builder b) {
        this.b = b;
    }

    public String host() {
        return b.host;
    }

    public int port() {
        return b.port;
    }

    public Map<String, String> initParams() {
        return b.initParams;
    }

    public String path() {
        return b.applicationPath;
    }


    public final static class Builder {
        private String applicationPath = "/";
        private String host = "localhost";
        private int port = 8080;
        private final Map<String, String> initParams = new HashMap<String, String>();

        public Builder path(String applicationPath) {
            this.applicationPath = applicationPath;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder addInitParam(String name, String value) {
            initParams.put(name, value);
            return this;
        }

        public Config build() {
            return new Config(this);
        }

    }

}
