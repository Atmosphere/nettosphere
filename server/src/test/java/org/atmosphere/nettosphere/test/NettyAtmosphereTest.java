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
package org.atmosphere.nettosphere.test;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.SSLEngineFactory;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Handler;
import org.atmosphere.nettosphere.Nettosphere;
import org.atmosphere.websocket.WebSocketEventListenerAdapter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
        AsyncHttpClient c = new AsyncHttpClient();
        c.prepareGet(targetUrl + "/suspend").setHeader(HeaderConfig.X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncHandler<Response>() {

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
        assertEquals(response.get().getResponseBody().trim(), RESUME);
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
                .resource(new Handler() {

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
                .resource(new Handler() {

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

    @Test
    public void httspHandlerTest() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final SSLContext sslContext = createSSLContext();
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .sslContext(sslContext)
                .resource(new Handler() {

                    @Override
                    public void handle(AtmosphereResource r) {
                        r.getResponse().write("Hello World from Nettosphere").closeStreamOrWriter();
                    }
                }).build();

        server = new Nettosphere.Builder().config(config).build();
        assertNotNull(server);
        server.start();

        AsyncHttpClient c = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setSSLEngineFactory(new SSLEngineFactory() {

            @Override
            public SSLEngine newSSLEngine() throws GeneralSecurityException {
                    SSLEngine sslEngine = sslContext.createSSLEngine();
                    sslEngine.setUseClientMode(true);
                    sslEngine.setEnabledCipherSuites(new String[]{"SSL_DH_anon_WITH_RC4_128_MD5"});
                    return sslEngine;
            }
        }).build());
        Response response = c.prepareGet("https://127.0.0.1:" + port).execute().get();
        assertNotNull(response);

        assertEquals(response.getResponseBody(), "Hello World from Nettosphere");

    }

    private static SSLContext createSSLContext() {
        try {
            InputStream keyStoreStream = BaseTest.class.getResourceAsStream("ssltest-cacerts.jks");
            char[] keyStorePassword = "changeit".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(keyStoreStream, keyStorePassword);

            // Set up key manager factory to use our key store
            char[] certificatePassword = "changeit".toCharArray();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, certificatePassword);

            // Initialize the SSLContext to work with our key managers.
            KeyManager[] keyManagers = kmf.getKeyManagers();
            TrustManager[] trustManagers = new TrustManager[]{DUMMY_TRUST_MANAGER};
            SecureRandom secureRandom = new SecureRandom();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, secureRandom);
            return sslContext;
        } catch (Exception e) {
            throw new Error("Failed to initialize SSLContext", e);
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

        final AtomicReference<HttpResponseHeaders> response = new AtomicReference<HttpResponseHeaders>();
        AsyncHttpClient c = new AsyncHttpClient();
        c.prepareGet(targetUrl + "/suspend").execute(new AsyncHandler<Response>() {

            final Response.ResponseBuilder b = new Response.ResponseBuilder();

            @Override
            public void onThrowable(Throwable t) {
                l.countDown();
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                return STATE.CONTINUE;
            }

            @Override
            public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                response.set(headers);
                l.countDown();
                return STATE.ABORT;
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
        AsyncHttpClient c = new AsyncHttpClient();
        c.prepareGet(targetUrl + "/suspend").setHeader(HeaderConfig.X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncHandler<Response>() {

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
        assertEquals(response.get().getResponseBody().trim(), RESUME);
    }
}
