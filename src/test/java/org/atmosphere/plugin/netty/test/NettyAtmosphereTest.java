/*
 * Copyright (c) 2010-2012 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.atmosphere.plugin.netty.test;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.plugin.netty.Config;
import org.atmosphere.plugin.netty.NettyAtmosphereServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

public class NettyAtmosphereTest {

    protected final Logger logger = LoggerFactory.getLogger(NettyAtmosphereTest.class);
    protected int port;
    protected NettyAtmosphereServer server;
    private String targetUrl;

    @AfterMethod(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        server.stop();
    }

    protected int findFreePort() throws IOException {
        ServerSocket socket = null;

        try {
            socket = new ServerSocket(0);

            return socket.getLocalPort();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = findFreePort();
        targetUrl = "http://127.0.0.1:" + port;
    }

    @Test
    public void suspendLongPollingTest() throws Exception {
        final String resume = "Resume";
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .path("/")
                .port(port)
                .host("127.0.0.1")
                .handler("/suspend", new AtmosphereHandler<HttpServletRequest, HttpServletResponse>() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource<HttpServletRequest, HttpServletResponse> r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1, false);
                            suspendCD.countDown();
                        } else {
                            r.getBroadcaster().broadcast(resume);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new NettyAtmosphereServer.Builder().config(config).build();

        assertNotNull(server);
        server.start();

        final AtomicReference<Response> response = new AtomicReference<Response>();
        AsyncHttpClient c = new AsyncHttpClient();
        c.prepareGet(targetUrl + "/suspend").execute(new AsyncHandler<Response>() {

            final Response.ResponseBuilder b = new Response.ResponseBuilder();

            @Override
            public void onThrowable(Throwable t) {
                l.countDown();
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                b.accumulate(bodyPart);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                b.accumulate(responseStatus);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                b.accumulate(headers);
                return STATE.CONTINUE;
            }

            @Override
            public Response onCompleted() throws Exception {
                response.set(b.build());

                l.countDown();
                return null;
            }
        });

        suspendCD.await(5, TimeUnit.SECONDS);

        Response r = c.prepareGet(targetUrl + "/suspend").execute().get();
        assertEquals(r.getStatusCode(), 200);

        l.await(5, TimeUnit.SECONDS);

        assertEquals(response.get().getStatusCode(), 200);
        assertEquals(response.get().getResponseBody(), resume);
    }

    @Test
    public void suspendStreamingTest() throws Exception {
       final String resume = "Resume";
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .path("/")
                .port(port)
                .host("127.0.0.1")
                .handler("/suspend", new AtmosphereHandler<HttpServletRequest, HttpServletResponse>() {

                    private final AtomicBoolean suspended = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource<HttpServletRequest, HttpServletResponse> r) throws IOException {
                        if (!suspended.getAndSet(true)) {
                            r.suspend(-1, true);
                            suspendCD.countDown();
                        } else {
                            r.getBroadcaster().broadcast(resume);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> r) throws IOException {
                        if (suspended.get()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new NettyAtmosphereServer.Builder().config(config).build();

        assertNotNull(server);
        server.start();

        final AtomicReference<Response> response = new AtomicReference<Response>();
        AsyncHttpClient c = new AsyncHttpClient();
        c.prepareGet(targetUrl + "/suspend").execute(new AsyncHandler<Response>() {

            final Response.ResponseBuilder b = new Response.ResponseBuilder();

            @Override
            public void onThrowable(Throwable t) {
                l.countDown();
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                b.accumulate(bodyPart);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                b.accumulate(responseStatus);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                b.accumulate(headers);
                return STATE.CONTINUE;
            }

            @Override
            public Response onCompleted() throws Exception {
                response.set(b.build());

                l.countDown();
                return null;
            }
        });

        suspendCD.await(5, TimeUnit.SECONDS);

        Response r = c.prepareGet(targetUrl + "/suspend").execute().get();
        assertEquals(r.getStatusCode(), 200);

        l.await(5, TimeUnit.SECONDS);

        assertEquals(response.get().getStatusCode(), 200);
        assertEquals(response.get().getResponseBody(), AtmosphereResourceImpl.createStreamingPadding("atmosphere") + resume);
    }
}
