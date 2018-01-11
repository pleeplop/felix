/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.http.sslfilter.internal;

import static junit.framework.Assert.assertEquals;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HTTP;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HTTPS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

public class SslFilterResponseTest
{
    private static final String BACKEND_SERVER = "backend.server";
    private static final String OTHER_SERVER = "other.server";

    private static final String PATH = "http://localhost:8080/";

    private static final String DEFAULT_HTTP_PORT = "80";
    private static final String ALT_HTTP_PORT = "8080";
    private static final String DEFAULT_HTTPS_PORT = "443";
    private static final String ALT_HTTPS_PORT = "8443";

    private static final String LOCATION = "Location";

    @Test
    public void testSetHttpLocationHeaderToNullValue() throws Exception
    {
        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        sresp.setHeader(LOCATION, null);

        assertEquals(null, resp.getHeader(LOCATION));
    }

    @Test
    public void testSetHttpsLocationHeaderToOriginalRequestURI() throws Exception
    {
        String location, expected;

        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        location = HTTPS + "://" + BACKEND_SERVER + "/foo";
        expected = location;

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }

    @Test
    public void testSetHttpLocationHeaderToOriginalRequestURI() throws Exception
    {
        String location, expected;

        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        location = HTTP + "://" + BACKEND_SERVER + "/foo";
        expected = HTTPS + "://" + BACKEND_SERVER + "/foo";

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }


    @Test
    public void testSetHttpLocationHeaderToOriginalRequestURIWithFragment() throws Exception
    {
        String location, expected;

        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        location = HTTP + "://" + BACKEND_SERVER + "/foo#abc";
        expected = HTTPS + "://" + BACKEND_SERVER + "/foo#abc";

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }


    @Test
    public void testSetHttpLocationHeaderToOriginalRequestWithExplicitPort() throws Exception
    {
        String location, expected;

        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        location = HTTP + "://" + BACKEND_SERVER + ":" + DEFAULT_HTTP_PORT + "/foo";
        expected = HTTPS + "://" + BACKEND_SERVER + "/foo";

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }

    @Test
    public void testSetHttpLocationHeaderToOriginalRequestWithForwardedPort() throws Exception
    {
        String location, expected;

        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, DEFAULT_HTTP_PORT, HTTPS, ALT_HTTPS_PORT, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        location = HTTP + "://" + BACKEND_SERVER + "/foo";
        expected = HTTPS + "://" + BACKEND_SERVER + ":" + ALT_HTTPS_PORT + "/foo";

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }

    @Test
    public void testSetHttpLocationHeaderToOriginalRequestWithDifferentPort() throws Exception
    {
        String location, expected;

        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        location = HTTP + "://" + BACKEND_SERVER + ":" + ALT_HTTP_PORT + "/foo";
        expected = location;

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }

    @Test
    public void testSetHttpLocationHeaderToOtherRequestURI() throws Exception
    {
        TestHttpServletResponse resp = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(resp, req);

        String location = HTTP + "://" + OTHER_SERVER + "/foo";
        String expected = location;

        sresp.setHeader(LOCATION, location);

        assertEquals(expected, resp.getHeader(LOCATION));
    }

    @Test
    public void testQueryString() throws Exception
    {
        TestHttpServletResponse response = createServletResponse();
        HttpServletRequest req = createServletRequest(BACKEND_SERVER, PATH);

        SslFilterResponse sresp = new SslFilterResponse(response, req);

        final String queryString = "?resource=%2Fen.html%3FpbOpen%3Dtrue&$$login$$=%24%24login%24%24&j_reason=errors.login.account.not.found";
        final String setUrl = "http://" + BACKEND_SERVER + "/" + queryString;
        final URI u = new URI(setUrl);
        final String expectedUrl = "https://" + BACKEND_SERVER + "/" + queryString;
        sresp.setHeader(SslFilterConstants.HDR_LOCATION, setUrl);

        assertEquals(expectedUrl, sresp.getHeader(SslFilterConstants.HDR_LOCATION));
    }

    private HttpServletRequest createServletRequest(String serverName, String requestURL)
    {
        return createServletRequest(serverName, DEFAULT_HTTP_PORT, HTTPS, DEFAULT_HTTPS_PORT, requestURL);
    }

    private HttpServletRequest createServletRequest(String serverName, String serverPort, String forwardedProto, String forwardedPort, String requestURL)
    {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServerName()).thenReturn(serverName);
        when(req.getServerPort()).thenReturn(Integer.parseInt(serverPort));
        when(req.getRequestURL()).thenReturn(new StringBuffer(requestURL));
        when(req.getHeader("X-Forwarded-Proto")).thenReturn(forwardedProto);
        when(req.getHeader("X-Forwarded-Port")).thenReturn(forwardedPort);
        return req;
    }

    private TestHttpServletResponse createServletResponse()
    {
        return new TestHttpServletResponse();
    }

    private static class TestHttpServletResponse implements HttpServletResponse
    {
        private final Map<String, String> headers = new HashMap<String, String>();
        private int status = -1;
        private boolean committed = false;

        @Override
        public void setLocale(Locale loc)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContentType(String type)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContentLength(int len)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContentLengthLong(long len)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCharacterEncoding(String charset)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setBufferSize(int size)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetBuffer()
        {
        }

        @Override
        public void reset()
        {
        }

        @Override
        public boolean isCommitted()
        {
            return this.committed;
        }

        @Override
        public PrintWriter getWriter() throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Locale getLocale()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getContentType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCharacterEncoding()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBufferSize()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flushBuffer() throws IOException
        {
            committed = true;
        }

        @Override
        public void setStatus(int sc, String sm)
        {
            status = sc;
            committed = true;
        }

        @Override
        public void setStatus(int sc)
        {
            status = sc;
            committed = true;
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            headers.put(name, Integer.toString(value));
        }

        @Override
        public void setHeader(String name, String value)
        {
            headers.put(name, value);
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendRedirect(String location) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendError(int sc, String msg) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendError(int sc) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getStatus()
        {
            return status;
        }

        @Override
        public Collection<String> getHeaders(String name)
        {
            return Collections.singleton(headers.get(name));
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return headers.keySet();
        }

        @Override
        public String getHeader(String name)
        {
            return headers.get(name);
        }

        @Override
        public String encodeUrl(String url)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String encodeURL(String url)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String encodeRedirectUrl(String url)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String encodeRedirectURL(String url)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsHeader(String name)
        {
            return headers.containsKey(name);
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addHeader(String name, String value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addDateHeader(String name, long date)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addCookie(Cookie cookie)
        {
            throw new UnsupportedOperationException();
        }
    }
}