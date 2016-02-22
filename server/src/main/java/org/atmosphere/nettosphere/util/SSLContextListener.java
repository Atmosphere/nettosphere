/*
 * Copyright 2015 Async-IO.org
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
package org.atmosphere.nettosphere.util;

import javax.net.ssl.SSLEngine;

/**
 * A callback used to configure {@link javax.net.ssl.SSLEngine} before they get injected in Netty.
 */
public interface SSLContextListener {

    SSLContextListener DEFAULT = new SSLContextListener(){

        @Override
        public void onPostCreate(SSLEngine e) {
            //TODO make the cipher suites configurable
            e.setEnabledCipherSuites(new String[]{"TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
                                                  "TLS_DH_anon_WITH_AES_128_CBC_SHA"});
            e.setUseClientMode(false);
        }
    };

    /**
     * Invoked just after the {@link SSLEngine} has been created, but not yet injected in Netty.
     * @param e SSLEngine;
     */
    public void onPostCreate(SSLEngine e);

}
