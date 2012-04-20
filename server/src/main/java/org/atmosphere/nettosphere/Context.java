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
package org.atmosphere.nettosphere;

import org.atmosphere.nettosphere.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Context implements ServletContext {
    private static final Logger logger = LoggerFactory.getLogger(Context.class);

    private final Builder b;

    private Context(Builder b) {
        this.b = b;
    }

    public final static class Builder {

        private String contextPath = "";
        private final Map<String, Object> attributes = new HashMap<String, Object>();
        private final Map<String, String> initParams = new HashMap<String, String>();
        private String basePath;

        public Builder putAttribute(String s, Object o) {
            attributes.put(s, o);
            return this;
        }

        public Builder contextPath(String s) {
            this.contextPath = s;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath.replaceAll("\\\\", "/");
            return this;
        }

        public Context build() {
            try {
                Thread.currentThread().setContextClassLoader(Utils.createURLClassLoader(basePath));
            } catch (IOException e) {
                logger.warn("", e);
            }
            return new Context(this);
        }

    }

    public String path() {
        return b.basePath;
    }

    @Override
    public String getContextPath() {
        return b.contextPath;
    }

    @Override
    public ServletContext getContext(String uripath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMajorVersion() {
        return 2;
    }

    @Override
    public int getMinorVersion() {
        return 5;
    }

    @Override
    public String getMimeType(String file) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set getResourcePaths(String path) {
        return _getResourcePaths(path, true);
    }

    private Set _getResourcePaths(String path, boolean resolve) {
        File a;
        if (resolve) {
            a = new File(b.basePath + path);
        } else {
            a = new File(path);
        }

        File[] files = a.listFiles();
        Set<String> s = new HashSet<String>();
        try {
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        Set inner = _getResourcePaths(f.getAbsolutePath(), false);
                        s.addAll(inner);
                    } else {
                        s.add(f.getCanonicalPath().substring(b.basePath.length()));
                    }
                }
            }
        } catch (IOException e) {
            logger.trace("", e);
        }
        return s;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
        if (!path.replace("\\\\", "/").startsWith("/")) {
            path = "/" + path;
        }
        return URI.create("file://" + b.basePath + path).toURL();
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        try {
            if (!path.replace("\\\\", "/").replace("\\", "/").startsWith("/")) {
                path = "/" + path;
            }
            return new FileInputStream(new File(b.basePath + path));
        } catch (FileNotFoundException e) {
            logger.trace("", e);
        }
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public Servlet getServlet(String name) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public Enumeration getServlets() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public Enumeration getServletNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

    @Deprecated
    @Override
    public void log(Exception exception, String msg) {
        logger.error(msg, exception);
    }

    @Override
    public void log(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    @Override
    public String getRealPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return b.basePath + path;
    }

    @Override
    public String getServerInfo() {
        return "Netty-Atmosphere/1.0";
    }

    @Override
    public String getInitParameter(String name) {
        return b.initParams.get(name);
    }

    @Override
    public Enumeration getInitParameterNames() {
        return Collections.enumeration(b.initParams.values());
    }

    @Override
    public Object getAttribute(String name) {
        return b.attributes.get(name);
    }

    @Override
    public Enumeration getAttributeNames() {
        return Collections.enumeration(b.attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object object) {
        b.attributes.put(name, object);
    }

    @Override
    public void removeAttribute(String name) {
        b.attributes.remove(name);
    }

    @Override
    public String getServletContextName() {
        return "Atmosphere";
    }
}

