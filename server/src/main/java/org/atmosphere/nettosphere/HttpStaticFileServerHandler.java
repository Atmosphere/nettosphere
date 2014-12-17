/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
 * Copyright 2014 Jeanfrancois Arcand
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

import org.atmosphere.nettosphere.util.MimeType;
import org.atmosphere.util.Version;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelFutureProgressListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.DATE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.IF_MODIFIED_SINCE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.LAST_MODIFIED;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * A simple handler that serves incoming HTTP requests to send their respective
 * HTTP responses.  It also implements {@code 'If-Modified-Since'} header to
 * take advantage of browser cache, as described in
 * <a href="http://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 * <p/>
 * <h3>How Browser Caching Works</h3>
 * <p/>
 * Web browser caching works with HTTP headers as illustrated by the following
 * sample:
 * <ol>
 * <li>Request #1 returns the content of <code>/file1.txt</code>.</li>
 * <li>Contents of <code>/file1.txt</code> is cached by the browser.</li>
 * <li>Request #2 for <code>/file1.txt</code> does return the contents of the
 * file again. Rather, a 304 Not Modified is returned. This tells the
 * browser to use the contents stored in its cache.</li>
 * <li>The server knows the file has not been modified because the
 * <code>If-Modified-Since</code> date is the same as the file's last
 * modified date.</li>
 * </ol>
 * <p/>
 * <pre>
 * Request #1 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 *
 * Response #1 Headers
 * ===================
 * HTTP/1.1 200 OK
 * Date:               Tue, 01 Mar 2011 22:44:26 GMT
 * Last-Modified:      Wed, 30 Jun 2010 21:36:48 GMT
 * Expires:            Tue, 01 Mar 2013 22:44:26 GMT
 * Cache-Control:      private, max-age=31536000
 *
 * Request #2 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 * If-Modified-Since:  Wed, 30 Jun 2010 21:36:48 GMT
 *
 * Response #2 Headers
 * ===================
 * HTTP/1.1 304 Not Modified
 * Date:               Tue, 01 Mar 2011 22:44:28 GMT
 *
 * </pre>
 */
public class HttpStaticFileServerHandler extends SimpleChannelUpstreamHandler {
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    private final static Logger logger = LoggerFactory.getLogger(HttpStaticFileServerHandler.class);

    public final static String STATIC_MAPPING = SimpleChannelUpstreamHandler.class.getName() + ".staticMapping";
    public final static String SERVICED = SimpleChannelUpstreamHandler.class.getName() + ".serviced";
    private final List<String> paths;
    private String defaultContentType = "text/html";

    public HttpStaticFileServerHandler(List<String> paths) {
        this.paths = paths;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        RandomAccessFile raf = null;
        boolean found = true;
        File file = null;
        for (String p : paths) {
            String path = p + sanitizeUri(request.getUri());
            if (path.endsWith("/") || path.endsWith(File.separator)) {
                path += "index.html";
            }

            if (path == null) {
                found = false;
                continue;
            }

            file = new File(path);
            if (file.isHidden() || !file.exists()) {
                found = false;
                continue;
            }
            if (!file.isFile()) {
                found = false;
                continue;
            }

            try {
                raf = new RandomAccessFile(file, "r");
                found = true;
                break;
            } catch (FileNotFoundException fnfe) {
                found = false;
                continue;
            }
        }

        if (!found) {
            sendError(ctx, NOT_FOUND, e);
            return;
        }
        request.headers().add(SERVICED, "true");

        // Cache Validation
        String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
        if (file != null && ifModifiedSince != null && ifModifiedSince.length() != 0) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client does
            // not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }

        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        contentType(request, response, file);
        setContentLength(response, fileLength);
        setDateAndCacheHeaders(response,file);

        Channel ch = e.getChannel();

        // Write the initial line and the header.
        ch.write(response);

        // Write the content.
        ChannelFuture writeFuture;
        if (ch.getPipeline().get(SslHandler.class) != null) {
            // Cannot use zero-copy with HTTPS.
            writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
        } else {
            // No encryption - use zero-copy.
            final FileRegion region =
                    new DefaultFileRegion(raf.getChannel(), 0, fileLength);
            writeFuture = ch.write(region);
            writeFuture.addListener(new ChannelFutureProgressListener() {
                public void operationComplete(ChannelFuture future) {
                    region.releaseExternalResources();
                }

                public void operationProgressed(
                        ChannelFuture future, long amount, long current, long total) {
                }
            });
        }

        // Decide whether to close the connection or not.
        if (!isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        Channel ch = e.getChannel();

        // Prevent recursion when the client close the connection during a write operation. In that
        // scenario the sendError will be invoked, but will fail since the channel has already been closed
        // For an unknown reason,
        if (ch.getAttachment() != null && Error.class.isAssignableFrom(ch.getAttachment().getClass())) {
            return;
        }

        Throwable cause = e.getCause();
        if (cause instanceof TooLongFrameException) {
            sendError(ctx, BAD_REQUEST, null);
            return;
        }

        ch.setAttachment(new Error());
        if (ch.isOpen()) {
            sendError(ctx, INTERNAL_SERVER_ERROR, null);
        }
    }

    protected String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }

        uri = uri.replace('/', File.separatorChar);

        if (uri.contains(File.separator + ".") ||
                uri.contains("." + File.separator) ||
                uri.startsWith(".") || uri.endsWith(".")) {
            return null;
        }

        int pos = uri.indexOf("?");
        if (pos != -1) {
            uri = uri.substring(0, pos);
        }
        return uri;
    }

    private static void sendNotModified(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().add(DATE, dateFormatter.format(time.getTime()));
    }

    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, MessageEvent event) {
        logger.trace("Error {} for {}", status, event);
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        response.headers().add(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().add(CONTENT_LENGTH, "0");
        response.headers().add("Server", "Atmosphere-" + Version.getRawVersion());

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    protected void contentType(HttpRequest request, HttpResponse response, File resource) {
        String substr;
        String uri = request.getUri();
        int dot = uri.lastIndexOf(".");
        if (dot < 0) {
            substr = resource.toString();
            dot = substr.lastIndexOf(".");
        } else {
            substr = uri;
        }

        if (dot > 0) {
            String ext = substr.substring(dot + 1);
            int queryString = ext.indexOf("?");
            if (queryString>0){
                ext.substring(0, queryString);
            }
            String contentType = MimeType.get(ext, defaultContentType);
            response.headers().add(HttpHeaders.Names.CONTENT_TYPE, contentType);
        } else {
            response.headers().add(HttpHeaders.Names.CONTENT_TYPE, defaultContentType);
        }

    }

    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().add(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().add(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().add(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().add(
                LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }
}
