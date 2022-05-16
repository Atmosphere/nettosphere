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

import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Response;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.HeaderConfig.LONG_POLLING_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.STREAMING_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class NettyJerseyTest extends BaseTest {

    protected static final Logger logger = LoggerFactory.getLogger(NettyJerseyTest.class);
    private Nettosphere server;

    String getUrlTarget(int port) {
        return "http://127.0.0.1:" + port + "/invoke";
    }

    @BeforeMethod
    public void startServer() throws IOException {
        port = findFreePort();
        urlTarget = getUrlTarget(port);
        Config config = new Config.Builder()
                .port(port)
                .host("127.0.0.1")
                .resource(Resource.class)
                .forceResponseWriteCompatibility(true)
                .build();
        server = new Nettosphere.Builder().config(config).build();
        server.start();
    }

    @AfterMethod
    public void stopServer(){
        server.stop();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testSuspendTimeout() {
        logger.info("{}: running test: testSuspendTimeout", getClass().getSimpleName());

        DefaultAsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            long t1 = System.currentTimeMillis();
            Response r = c.prepareGet(urlTarget).setHeader(X_ATMOSPHERE_TRANSPORT, LONG_POLLING_TRANSPORT).execute().get(20, TimeUnit.SECONDS);
            assertNotNull(r);
            assertEquals(r.getStatusCode(), 200);
            String resume = r.getResponseBody();
            assertEquals(resume.trim(), "resume");
            long current = System.currentTimeMillis() - t1;
            assertTrue(current > 5000 && current < 15000);
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = false)
    public void testProgrammaticResume() throws IOException {
        logger.info("{}: running test: testProgrammaticResume", getClass().getSimpleName());

        AsyncHttpClient c = new DefaultAsyncHttpClient();
        final AtomicReference<String> location = new AtomicReference<String>();
        final AtomicReference<String> response = new AtomicReference<String>("");
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch locationLatch = new CountDownLatch(1);
        try {
            c.prepareGet(urlTarget + "/suspendAndResume").execute(new AsyncHandler<String>() {

                public void onThrowable(Throwable throwable) {
                    fail("onThrowable", throwable);
                }

                public State onBodyPartReceived(HttpResponseBodyPart bp) throws Exception {

                    logger.info("body part byte string: {}", new String(bp.getBodyPartBytes()));
                    response.set(response.get() + new String(bp.getBodyPartBytes()));
                    locationLatch.countDown();
                    return State.CONTINUE;
                }

                public State onStatusReceived(HttpResponseStatus hs) throws Exception {
                    return State.CONTINUE;
                }

                public State onHeadersReceived(HttpHeaders rh) throws Exception {
                    location.set(rh.get("Location"));
                    return State.CONTINUE;
                }

                public String onCompleted() throws Exception {
                    latch.countDown();
                    return "";
                }
            });

            locationLatch.await(5, TimeUnit.SECONDS);

            Response r = c.prepareGet(location.get()).execute().get(10, TimeUnit.SECONDS);
            latch.await(20, TimeUnit.SECONDS);
            assertNotNull(r);
            assertEquals(r.getStatusCode(), 200);
            assertEquals(response.get(), "suspendresume");

        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testResumeOnBroadcastUsingBroadcasterFactory() throws IOException {
        logger.info("{}: running test: testResumeOnBroadcast", getClass().getSimpleName());

        AsyncHttpClient c = new DefaultAsyncHttpClient();
        long t1 = System.currentTimeMillis();

        try {
            Response r = c.prepareGet(urlTarget + "/subscribeAndUsingExternalThread").setHeader(X_ATMOSPHERE_TRANSPORT, LONG_POLLING_TRANSPORT).execute().get();
            assertNotNull(r);
            assertEquals(r.getStatusCode(), 200);
            long current = System.currentTimeMillis() - t1;
            assertTrue(current > 5000 && current < 10000);
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testDelayBroadcast() throws IOException {
        logger.info("{}: running test: testDelayBroadcast", getClass().getSimpleName());

        final CountDownLatch latch = new CountDownLatch(1);
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            final AtomicReference<Response> response = new AtomicReference<Response>();
            c.prepareGet(urlTarget + "/forever").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncCompletionHandler<Response>() {

                @Override
                public Response onCompleted(Response r) throws Exception {
                    try {
                        response.set(r);
                        return r;
                    } finally {
                        latch.countDown();
                    }
                }
            });

            // Let Atmosphere suspend the connections.
            Thread.sleep(2500);
            c.preparePost(urlTarget + "/delay").addFormParam("message", "foo").execute().get();
            c.preparePost(urlTarget + "/publishAndResume").addFormParam("message", "bar").execute().get();

            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }

            Response r = response.get();

            assertNotNull(r);
            assertEquals(r.getResponseBody().trim(), "foo\nbar");
            assertEquals(r.getStatusCode(), 200);
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testDelayNextBroadcast() throws IOException {
        logger.info("{}: running test: testDelayNextBroadcast", getClass().getSimpleName());

        final CountDownLatch latch = new CountDownLatch(1);
        long t1 = System.currentTimeMillis();

        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            final AtomicReference<Response> response = new AtomicReference<Response>();
            c.prepareGet(urlTarget + "/forever").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncCompletionHandler<Response>() {

                @Override
                public Response onCompleted(Response r) throws Exception {
                    try {
                        response.set(r);
                        return r;
                    } finally {
                        latch.countDown();
                    }
                }
            });

            // Let Atmosphere suspend the connections.
            Thread.sleep(2500);
            c.preparePost(urlTarget + "/delay").addFormParam("message", "foo").execute().get();
            c.preparePost(urlTarget + "/delayAndResume").addFormParam("message", "bar").execute().get();

            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }

            Response r = response.get();

            assertNotNull(r);
            assertEquals(r.getResponseBody().trim(), "foo\nbar");
            assertEquals(r.getStatusCode(), 200);
            long current = System.currentTimeMillis() - t1;
            assertTrue(current > 5000 && current < 10000);
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testScheduleBroadcast() throws IOException {
        logger.info("{}: running test: testScheduleBroadcast", getClass().getSimpleName());

        final CountDownLatch latch = new CountDownLatch(1);
        long t1 = System.currentTimeMillis();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            final AtomicReference<Response> response = new AtomicReference<Response>();
            c.prepareGet(urlTarget + "/foreverWithoutComments").setHeader(X_ATMOSPHERE_TRANSPORT, STREAMING_TRANSPORT).execute(new AsyncCompletionHandler<Response>() {

                @Override
                public Response onCompleted(Response r) throws Exception {
                    try {
                        response.set(r);
                        return r;
                    } finally {
                        latch.countDown();
                    }
                }
            });

            // Let Atmosphere suspend the connections.
            Thread.sleep(2500);
            c.preparePost(urlTarget + "/scheduleAndResume").addFormParam("message", "foo").execute().get();

            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }

            Response r = response.get();
            assertNotNull(r);
            assertEquals(r.getStatusCode(), 200);
            assertEquals(r.getResponseBody().trim(), "foo");
            long current = System.currentTimeMillis() - t1;
            assertTrue(current > 5000 && current < 10000);
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testBroadcastFilter() throws IOException {
        logger.info("{}: running test: testBroadcastFilter", getClass().getSimpleName());

        final CountDownLatch latch = new CountDownLatch(1);
        long t1 = System.currentTimeMillis();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            final AtomicReference<Response> response = new AtomicReference<Response>();
            c.prepareGet(urlTarget + "/foreverWithoutComments").setHeader(X_ATMOSPHERE_TRANSPORT, STREAMING_TRANSPORT).execute(new AsyncCompletionHandler<Response>() {

                @Override
                public Response onCompleted(Response r) throws Exception {
                    try {
                        response.set(r);
                        return r;
                    } finally {
                        latch.countDown();
                    }
                }
            });

            // Let Atmosphere suspend the connections.
            Thread.sleep(4000);
            c.preparePost(urlTarget + "/filter").addFormParam("message", "<script>foo</script>").execute().get();

            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }

            Response r = response.get();
            assertNotNull(r);
            assertEquals(r.getStatusCode(), 200);
            assertEquals(r.getResponseBody().trim(), "&lt;script&gt;foo&lt;/script&gt;<br />");
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = false)
    public void testAggregateFilter() throws IOException {
        logger.info("{}: running test: testAggregateFilter", getClass().getSimpleName());

        final CountDownLatch latch = new CountDownLatch(1);
        long t1 = System.currentTimeMillis();
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            final AtomicReference<Response> response = new AtomicReference<Response>();
            c.prepareGet(urlTarget + "/foreverWithoutComments").execute(new AsyncCompletionHandler<Response>() {

                @Override
                public Response onCompleted(Response r) throws Exception {
                    try {
                        response.set(r);
                        return r;
                    } finally {
                        latch.countDown();
                    }
                }
            });

            // Let Atmosphere suspend the connections.
            Thread.sleep(2500);
            for (int i = 0; i < 10; i++) {
                c.preparePost(urlTarget + "/aggregate").addFormParam("message",
                        "==================================================").execute().get(5, TimeUnit.SECONDS);
            }


            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }

            Response r = response.get();
            assertNotNull(r);
            assertEquals(r.getStatusCode(), 200);
            assertEquals(r.getResponseBody(), "==================================================\n" +
                    "==================================================\n" +
                    "==================================================\n" +
                    "==================================================\n" +
                    "==================================================\n" +
                    "==================================================\n");
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }

        c.close();
    }

    @Test(timeOut = 20000, enabled = true)
    public void testProgrammaticDelayBroadcast() throws IOException {
        logger.info("{}: running test: testDelayBroadcast", getClass().getSimpleName());

        final CountDownLatch latch = new CountDownLatch(1);
        AsyncHttpClient c = new DefaultAsyncHttpClient();
        try {
            final AtomicReference<Response> response = new AtomicReference<Response>();
            c.prepareGet(urlTarget + "/forever").setHeader(X_ATMOSPHERE_TRANSPORT, "streaming").execute(new AsyncCompletionHandler<Response>() {

                @Override
                public Response onCompleted(Response r) throws Exception {
                    try {
                        response.set(r);
                        return r;
                    } finally {
                        latch.countDown();
                    }
                }
            });

            // Let Atmosphere suspend the connections.
            Thread.sleep(2500);
            Response r = c.preparePost(urlTarget + "/programmaticDelayBroadcast").addFormParam("message", "foo").execute().get();
            assertEquals(r.getStatusCode(), 200);
            r = c.preparePost(urlTarget + "/publishAndResume").addFormParam("message", "bar").execute().get();
            assertEquals(r.getStatusCode(), 200);

            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }

            r = response.get();

            assertNotNull(r);
            assertEquals("foobar", r.getResponseBody().trim());
            assertEquals(r.getStatusCode(), 200);
        } catch (Exception e) {
            logger.error("test failed", e);
            fail(e.getMessage());
        }
        c.close();
    }
}
