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
package org.atmosphere.nettosphere.test;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Handler;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.websocket.WebSocketEventListenerAdapter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class NettyAtmosphereTest extends BaseTest {
    private final static String RESUME = "Resume";

    protected int port;
    protected Nettosphere server;
    private String targetUrl;
    private String wsUrl;

    @AfterMethod(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        server.stop();
    }

    @BeforeMethod(alwaysRun = true)
    public void start() throws IOException {
        port = findFreePort();
        targetUrl = "http://127.0.0.1:" + port;
        wsUrl = "ws://127.0.0.1:" + port;
    }

    @Test
    public void initParamTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final AtomicBoolean b = new AtomicBoolean(false);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .initParam("foo", "bar")
                .resource("/suspend", new AtmosphereHandler() {

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (r.getAtmosphereConfig().getInitParameter("foo") != null) {
                            b.set(true);
                        }
                        l.countDown();
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();

        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new AsyncHttpClient();
        Response r = c.prepareGet(targetUrl + "/suspend").execute().get();

        assertEquals(r.getStatusCode(), 200);
        assertEquals(b.get(), true);

    }

    @Test
    public void suspendLongPollingTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.addEventListener(new WebSocketEventListenerAdapter() {
                                @Override
                                public void onSuspend(AtmosphereResourceEvent e) {
                                    suspendCD.countDown();
                                }
                            });
                            r.suspend(-1, false);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (r.isSuspended()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();

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
        assertEquals(response.get().getResponseBody(), RESUME);
    }

    @Test
    public void suspendStreamingTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean suspended = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!suspended.getAndSet(true)) {
                            r.suspend(-1, true);
                            suspendCD.countDown();
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (suspended.get()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();

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
        assertEquals(response.get().getResponseBody(), AtmosphereResourceImpl.createStreamingPadding("atmosphere") + RESUME);
    }

    @Test
    public void suspendWebSocketTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1, false);
                        } else {
                            r.getBroadcaster().broadcast(RESUME);
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (!r.isResuming() || !r.isCancelled()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            r.getResource().resume();
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new AsyncHttpClient();
        WebSocket webSocket = c.prepareGet(wsUrl + "/suspend").execute(new WebSocketUpgradeHandler.Builder().build()).get();
        assertNotNull(webSocket);
        webSocket.addWebSocketListener(new WebSocketTextListener() {
            @Override
            public void onMessage(String message) {
                response.set(message);
                l.countDown();
            }

            @Override
            public void onFragment(String fragment, boolean last) {
            }

            @Override
            public void onOpen(WebSocket websocket) {
            }

            @Override
            public void onClose(WebSocket websocket) {
                l.countDown();
            }

            @Override
            public void onError(Throwable t) {
                l.countDown();
            }
        }).sendTextMessage("Ping");

        l.await(5, TimeUnit.SECONDS);

        webSocket.close();
        assertEquals(response.get(), RESUME);

    }

    @Test
    public void webSocketHandlerTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .handler(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new AsyncHttpClient();
        WebSocket webSocket = c.prepareGet(wsUrl).execute(new WebSocketUpgradeHandler.Builder().build()).get();
        assertNotNull(webSocket);
        webSocket.addWebSocketListener(new WebSocketTextListener() {
            @Override
            public void onMessage(String message) {
                response.set(message);
                l.countDown();
            }

            @Override
            public void onFragment(String fragment, boolean last) {
            }

            @Override
            public void onOpen(WebSocket websocket) {
            }

            @Override
            public void onClose(WebSocket websocket) {
            }

            @Override
            public void onError(Throwable t) {
            }
        });

        l.await(5, TimeUnit.SECONDS);

        webSocket.close();
        assertEquals(response.get(), "Hello World from Nettosphere");

    }

    @Test
    public void httpHandlerTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .handler(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new AsyncHttpClient();
        Response response = c.prepareGet(targetUrl).execute().get();
        assertNotNull(response);

        assertEquals(response.getResponseBody(), "Hello World from Nettosphere");

    }

}
