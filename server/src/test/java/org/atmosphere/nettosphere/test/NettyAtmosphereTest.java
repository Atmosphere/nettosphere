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
package org.atmosphere.nettosphere.test;

import static org.atmosphere.cpr.HeaderConfig.LONG_POLLING_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.*;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Response;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Handler;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.websocket.WebSocketEventListenerAdapter;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketHandlerAdapter;
import org.atmosphere.websocket.WebSocketPingPongListener;
import org.atmosphere.websocket.WebSocketProcessor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

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

        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            Response r = c.prepareGet(targetUrl + "/suspend").execute().get();

            assertEquals(r.getStatusCode(), 200);
            assertEquals(b.get(), true);
        } finally {
            c.close();
        }
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
                            r.suspend(-1);
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            c.prepareGet(targetUrl + "/suspend").setHeader(X_ATMOSPHERE_TRANSPORT, LONG_POLLING_TRANSPORT).execute(new AsyncHandler<Response>() {

                final Response.ResponseBuilder b = new Response.ResponseBuilder();

                @Override
                public void onThrowable(Throwable t) {
                    l.countDown();
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    b.accumulate(bodyPart);
                    return State.CONTINUE;
                }

                @Override
                public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    b.accumulate(responseStatus);
                    return State.CONTINUE;
                }

                @Override
                public State onHeadersReceived(HttpHeaders headers) throws Exception {
                    b.accumulate(headers);
                    return State.CONTINUE;
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
            assertEquals(response.get().getResponseBody().trim(), RESUME);
        } finally {
            c.close();
        }
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
                            r.suspend(-1);
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            c.prepareGet(targetUrl + "/suspend").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncHandler<Response>() {

                final Response.ResponseBuilder b = new Response.ResponseBuilder();

                @Override
                public void onThrowable(Throwable t) {
                    l.countDown();
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    b.accumulate(bodyPart);
                    return State.CONTINUE;
                }

                @Override
                public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    b.accumulate(responseStatus);
                    return State.CONTINUE;
                }

                @Override
                public State onHeadersReceived(HttpHeaders headers) throws Exception {
                    b.accumulate(headers);
                    return State.CONTINUE;
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
            assertEquals(response.get().getResponseBody().trim(), RESUME);
        } finally {
            c.close();
        }
    }

    @Test
    public void subProtocolTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .subProtocols("jfa-protocol")
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!b.getAndSet(true)) {
                            r.suspend(-1);
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl + "/suspend")
                    .addHeader("Sec-WebSocket-Protocol", "jfa-protocol").execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                    l.countDown();
                }

                @Override
                public void onError(Throwable t) {
                    l.countDown();
                }
            }).sendTextFrame("Ping");

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), RESUME);
        } finally {
            c.close();
        }
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
                            r.suspend(-1);
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl + "/suspend").execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                    l.countDown();
                }

                @Override
                public void onError(Throwable t) {
                    l.countDown();
                }
            }).sendTextFrame("Ping");

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), RESUME);
        } finally {
            c.close();
        }
    }

    @Test
    public void webSocketHandlerTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final AtomicBoolean handshake = new AtomicBoolean(true);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        if (!handshake.getAndSet(false)) {
                            r.getResponse().write("Hello World from Nettosphere");
                        }
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl).execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                }

                @Override
                public void onError(Throwable t) {
                }
            });
            webSocket.sendTextFrame("Hello World from Nettosphere");

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), "Hello World from Nettosphere");
        } finally {
            c.close();
        }
    }

    @Test
    public void webSocketFragmenterTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final AtomicBoolean handshake = new AtomicBoolean(true);
        String responseText = "Hello World from Nettosphere";

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .maxWebSocketFrameSize(responseText.length() - 2)
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        if (!handshake.getAndSet(false)) {
                            r.getResponse().write(responseText);
                        }
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl).execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                }

                @Override
                public void onError(Throwable t) {
                }
            });
            webSocket.sendTextFrame("Hello");

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), responseText);
        } finally {
            c.close();
        }
    }

    @Test
    public void textFrameAsBinaryTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        final AtomicBoolean handshake = new AtomicBoolean(true);
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .textFrameAsBinary(true)
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        if (!handshake.getAndSet(false))
                            r.getResponse().write(r.getRequest().body().asBytes()).closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl).execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                }

                @Override
                public void onError(Throwable t) {
                }
            });
            webSocket.sendTextFrame("Hello World from Nettosphere");

            l.await(5, TimeUnit.SECONDS);
            webSocket.sendCloseFrame();
            assertEquals(response.get(), "Hello World from Nettosphere");
        } finally {
            c.close();
        }
    }

    @Test
    public void wsFramingTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/*", new WebSocketHandlerAdapter() {

                    @Override
                    public void onTextMessage(org.atmosphere.websocket.WebSocket webSocket, String data) throws IOException {
                        webSocket.write(data);
                    }

                })
                .build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .build());
        try {
            final AtomicReference<String> response = new AtomicReference<String>();
            WebSocket webSocket = c.prepareGet(wsUrl).execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                }

                @Override
                public void onError(Throwable t) {
                }
            });

            webSocket.sendTextFrame("ECHO", false, 0);
            webSocket.sendContinuationFrame("ECHO", true, 0);

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), "ECHOECHO");
        } finally {
            c.close();
        }
    }

    @Test
    public void httpHandlerTest() throws Exception {

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            Response response = c.prepareGet(targetUrl).execute().get();
            assertNotNull(response);

            assertEquals(response.getResponseBody(), "Hello World from Nettosphere");
        } finally {
            c.close();
        }
    }

    @Test
    public void httpsHandlerTest() throws Exception {
        final SslContext sslClientContext = SslContextBuilder.forClient().build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslServer = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .sslContext(sslServer)
                .enabledCipherSuites(sslServer.cipherSuites().toArray(new String[]{}))
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setEnabledCipherSuites(sslClientContext.cipherSuites().toArray(new String[]{}))
                .setUseInsecureTrustManager(true)
                .build());
        try {
            Response response = c.prepareGet("https://127.0.0.1:" + port).execute().get();
            assertNotNull(response);

            assertEquals(response.getResponseBody(), "Hello World from Nettosphere");
        } finally {
            c.close();
        }
    }

    @Test
    public void nettySslContextTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .sslContext(sslCtx)
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
        		.setUseInsecureTrustManager(true)
                .build());
        try {
            final AtomicReference<String> response = new AtomicReference<String>();
            WebSocket webSocket = c.prepareGet("wss://127.0.0.1:" + port).execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                }

                @Override
                public void onError(Throwable t) {
                }
            });

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), "Hello World from Nettosphere");
        } finally {
            c.close();
        }
    }

    @Test
    public void wssHandlerTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final SslContext sslClientContext = SslContextBuilder.forClient().build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslServer = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        final int port = findFreePort();
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .sslContext(sslServer)
                .enabledCipherSuites(sslServer.cipherSuites().toArray(new String[]{}))
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
        		.setUseInsecureTrustManager(true)
                .setEnabledCipherSuites(sslClientContext.cipherSuites().toArray(new String[]{}))
                .build());
        try {
            final AtomicReference<String> response = new AtomicReference<String>();
            WebSocket webSocket = c.prepareGet("wss://127.0.0.1:" + port).execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                }

                @Override
                public void onError(Throwable t) {
                }
            });

            l.await(30, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), "Hello World from Nettosphere");
        } finally {
            c.close();
        }
    }


    private static final AtomicBoolean TRUST_SERVER_CERT = new AtomicBoolean(true);
    private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!TRUST_SERVER_CERT.get()) {
                throw new CertificateException("Server certificate not trusted.");
            }
        }
    };

    @Test
    public void closeTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {


                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        r.addEventListener(new WebSocketEventListenerAdapter() {
                            @Override
                            public void onSuspend(AtmosphereResourceEvent e) {
                                suspendCD.countDown();
                            }
                        });
                        r.suspend(60, TimeUnit.SECONDS);
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

        final AtomicReference<HttpHeaders> response = new AtomicReference<HttpHeaders>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            c.prepareGet(targetUrl + "/suspend?" + X_ATMOSPHERE_TRANSPORT + "=" + HeaderConfig.LONG_POLLING_TRANSPORT).execute(new AsyncHandler<Response>() {

                @Override
                public void onThrowable(Throwable t) {
                    l.countDown();
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    return State.CONTINUE;
                }

                @Override
                public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    return State.CONTINUE;
                }

                @Override
                public State onHeadersReceived(HttpHeaders headers) throws Exception {
                    response.set(headers);
                    l.countDown();
                    return State.ABORT;
                }

                @Override
                public Response onCompleted() throws Exception {
                    return null;
                }
            });

            suspendCD.await(5, TimeUnit.SECONDS);

            Thread.sleep(2000);

            server.stop();
            l.await(20, TimeUnit.SECONDS);

            assertNotNull(response.get());
        } finally {
            c.close();
        }
    }

    @Test
    public void suspendNoChunkStreamingTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .supportChunking(false)
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean suspended = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!suspended.getAndSet(true)) {
                            r.suspend(-1);
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            c.prepareGet(targetUrl + "/suspend").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncHandler<Response>() {

                final Response.ResponseBuilder b = new Response.ResponseBuilder();

                @Override
                public void onThrowable(Throwable t) {
                    l.countDown();
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    b.accumulate(bodyPart);
                    return State.CONTINUE;
                }

                @Override
                public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    b.accumulate(responseStatus);
                    return State.CONTINUE;
                }

                @Override
                public State onHeadersReceived(HttpHeaders headers) throws Exception {
                    b.accumulate(headers);
                    return State.CONTINUE;
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
            assertEquals(response.get().getResponseBody().trim(), RESUME);
        } finally {
            c.close();
        }
    }

    @Test
    public void aggregatePostInMemoryTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .supportChunking(false)
                .aggregateRequestBodyInMemory(false)
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean suspended = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!suspended.getAndSet(true)) {
                            r.suspend(-1);
                            suspendCD.countDown();
                        } else {
                            r.getBroadcaster().broadcast(new String(r.getRequest().body().asBytes()));
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (suspended.get()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            if (r.getMessage().toString().contains("message")) {
                                r.getResource().resume();
                            }
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            c.prepareGet(targetUrl + "/suspend").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncHandler<Response>() {

                final Response.ResponseBuilder b = new Response.ResponseBuilder();

                @Override
                public void onThrowable(Throwable t) {
                    l.countDown();
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    b.accumulate(bodyPart);
                    return State.CONTINUE;
                }

                @Override
                public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    b.accumulate(responseStatus);
                    return State.CONTINUE;
                }

                @Override
                public State onHeadersReceived(HttpHeaders headers) throws Exception {
                    b.accumulate(headers);
                    return State.CONTINUE;
                }

                @Override
                public Response onCompleted() throws Exception {
                    response.set(b.build());

                    l.countDown();
                    return null;
                }
            });

            suspendCD.await(5, TimeUnit.SECONDS);

            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                b.append("======");
            }
            b.append("message");

            Response r = c.preparePost(targetUrl + "/suspend").setBody(b.toString()).execute().get();
            assertEquals(r.getStatusCode(), 200);

            l.await(5, TimeUnit.SECONDS);

            assertEquals(response.get().getStatusCode(), 200);
            assertEquals(response.get().getResponseBody().trim(), b.toString());
        } finally {
            c.close();
        }
    }

    @Test
    public void chunkPostInMemoryTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch suspendCD = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .supportChunking(true)
                .aggregateRequestBodyInMemory(false)
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean suspended = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        if (!suspended.getAndSet(true)) {
                            r.suspend(-1);
                            suspendCD.countDown();
                        } else {
                            r.getBroadcaster().broadcast(new String(r.getRequest().body().asBytes()));
                        }
                    }

                    @Override
                    public void onStateChange(AtmosphereResourceEvent r) throws IOException {
                        if (r.isSuspended()) {
                            r.getResource().getResponse().getWriter().print(r.getMessage());
                            if (r.getMessage().toString().contains("message")) {
                                r.getResource().resume();
                            }
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
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            c.prepareGet(targetUrl + "/suspend").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncHandler<Response>() {

                final Response.ResponseBuilder b = new Response.ResponseBuilder();

                @Override
                public void onThrowable(Throwable t) {
                    l.countDown();
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    b.accumulate(bodyPart);
                    return State.CONTINUE;
                }

                @Override
                public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    b.accumulate(responseStatus);
                    return State.CONTINUE;
                }

                @Override
                public State onHeadersReceived(HttpHeaders headers) throws Exception {
                    b.accumulate(headers);
                    return State.CONTINUE;
                }

                @Override
                public Response onCompleted() throws Exception {
                    response.set(b.build());

                    l.countDown();
                    return null;
                }
            });

            suspendCD.await(5, TimeUnit.SECONDS);

            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                b.append("======");
            }
            b.append("message");

            Response r = c.preparePost(targetUrl + "/suspend").setBody(b.toString()).execute().get();
            assertEquals(r.getStatusCode(), 200);

            l.await(5, TimeUnit.SECONDS);

            assertEquals(response.get().getStatusCode(), 200);
            assertEquals(response.get().getResponseBody().trim(), b.toString());
        } finally {
            c.close();
        }
    }

    public final static class PingPongHandler implements WebSocketHandler, WebSocketPingPongListener {

        @Override
        public void onByteMessage(org.atmosphere.websocket.WebSocket webSocket, byte[] data, int offset, int length) throws IOException {

        }

        @Override
        public void onTextMessage(org.atmosphere.websocket.WebSocket webSocket, String data) throws IOException {

        }

        @Override
        public void onOpen(org.atmosphere.websocket.WebSocket webSocket) throws IOException {

        }

        @Override
        public void onClose(org.atmosphere.websocket.WebSocket webSocket) {

        }

        @Override
        public void onError(org.atmosphere.websocket.WebSocket webSocket, WebSocketProcessor.WebSocketException t) {

        }

        @Override
        public void onPong(org.atmosphere.websocket.WebSocket webSocket, byte[] bytes, int i, int i1) {
            webSocket.sendPing("Hello from server".getBytes());
        }

        @Override
        public void onPing(org.atmosphere.websocket.WebSocket webSocket, byte[] bytes, int i, int i1) {
            webSocket.sendPong("Hello from server".getBytes());
        }
    }

    @Test
    public void pingTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/pingPong", PingPongHandler.class).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl + "/pingPong").execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onPongFrame(byte[] message) {
                    response.set(new String(message));
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {

                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {

                }

                @Override
                public void onError(Throwable t) {

                }
            }).sendPingFrame("Hello from Client".getBytes());

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), "Hello from server");
        } finally {
            c.close();
        }
    }

    @Test
    public void pongTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource("/pingPong", PingPongHandler.class).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl + "/pingPong").execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onPingFrame(byte[] message) {
                    response.set(new String(message));
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {

                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {

                }

                @Override
                public void onError(Throwable t) {

                }
            }).sendPongFrame("Hello from Client".getBytes());

            l.await(5, TimeUnit.SECONDS);

            webSocket.sendCloseFrame();
            assertEquals(response.get(), "Hello from server");
        } finally {
            c.close();
        }
    }


    @Test
    public void timeoutTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);

        Config config = new Config.Builder()
                .port(port)
                .initParam(ApplicationConfig.WEBSOCKET_IDLETIME, "5000")
                .subProtocols("jfa-protocol")
                .host("127.0.0.1")
                .resource("/suspend", new AtmosphereHandler() {

                    private final AtomicBoolean b = new AtomicBoolean(false);

                    @Override
                    public void onRequest(AtmosphereResource r) throws IOException {
                        r.suspend(-1);
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

        final AtomicReference<String> response = new AtomicReference<String>();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            WebSocket webSocket = c.prepareGet(wsUrl + "/suspend")
                    .addHeader("Sec-WebSocket-Protocol", "jfa-protocol").execute(new WebSocketUpgradeHandler.Builder().build()).get();
            assertNotNull(webSocket);
            webSocket.addWebSocketListener(new WebSocketListener() {
                @Override
                public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    response.set(payload);
                    l.countDown();
                }

                @Override
                public void onOpen(WebSocket websocket) {
                }

                @Override
                public void onClose(WebSocket websocket, int code, String reason) {
                    l.countDown();
                }

                @Override
                public void onError(Throwable t) {
                }
            });

            l.await(10, TimeUnit.SECONDS);

            assertNull(response.get());
            assertEquals(l.getCount(), 0);
        } finally {
            c.close();
        }
    }
}
