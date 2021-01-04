
package org.atmosphere.nettosphere;

import io.netty.channel.Channel;
import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ChannelWriterTest {

    public static final HashMap<String, String> EMPTY_HEADERS = new HashMap<String, String>();

    private Channel channelMock;
    private ChannelWriter sut;

    @BeforeMethod
    public void setUp() throws Exception {
        channelMock = mock(Channel.class);
        sut = new ChannelWriter(channelMock, true, false) {
            @Override
            public AsyncIOWriter asyncWrite(AtmosphereResponse response, byte[] data, int offset, int length) throws IOException {
                throw new RuntimeException("Not implemented for test.");
            }
        };
    }

    @Test
    public void testHeaders() throws Exception {
        AtmosphereResponse response = setupResponse(EMPTY_HEADERS);
        int contentLength = -1;

        String result = sut.constructStatusAndHeaders(response, contentLength);

        assertEquals("HTTP/1.1 200 OK\r\n" +
            "Content-Type:text/plain\r\n" +
            "\r\n", result);

        verifyInteractions(response);
    }

    @Test
    public void testHeadersWithLength() throws Exception {
        AtmosphereResponse response = setupResponse(EMPTY_HEADERS);
        int contentLength = 10;

        String result = sut.constructStatusAndHeaders(response, contentLength);

        assertEquals("HTTP/1.1 200 OK\r\n" +
            "Content-Type:text/plain\r\n" +
            "Content-Length:" + contentLength + "\r\n" +
            "\r\n", result);

        verifyInteractions(response);
    }

    @Test
    public void testCustomHeaders() throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Custom-Header", "value");

        AtmosphereResponse response = setupResponse(headers);
        int contentLength = 100;

        String result = sut.constructStatusAndHeaders(response, contentLength);

        assertEquals("HTTP/1.1 200 OK\r\n" +
            "Content-Type:text/plain\r\n" +
            "Content-Length:" + contentLength + "\r\n" +
            "X-Custom-Header:value\r\n" +
            "\r\n", result);

        verifyInteractions(response);
    }

    @Test
    public void testCustomContentType() throws Exception {
        String contentType = "application/json";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", contentType);
        headers.put("X-Custom-Header", "value");

        AtmosphereResponse response = setupResponse(headers);
        int contentLength = 100;

        String result = sut.constructStatusAndHeaders(response, contentLength);

        assertEquals("HTTP/1.1 200 OK\r\n" +
            "Content-Type:" + contentType + "\r\n" +
            "Content-Length:" + contentLength + "\r\n" +
            "X-Custom-Header:value\r\n" +
            "\r\n", result);

        verifyInteractions(response);
    }

    private AtmosphereResponse setupResponse(Map<String, String> headers) {
        AtmosphereResponse response = mock(AtmosphereResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getStatusMessage()).thenReturn("OK");
        when(response.getContentType()).thenReturn("text/plain");
        when(response.headers()).thenReturn(headers);
        return response;
    }

    private void verifyInteractions(AtmosphereResponse response) {
        verify(response).getStatus();
        verify(response).getStatusMessage();
        verify(response).getContentType();
        verify(response).headers();
        verifyNoMoreInteractions(channelMock, response);
    }

}
