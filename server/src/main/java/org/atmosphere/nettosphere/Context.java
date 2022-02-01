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
package org.atmosphere.nettosphere;

import org.atmosphere.nettosphere.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Context {
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

        public Builder basePath(List<String> paths) {

            String basePath = ".";
            for (String p: paths) {
                if (new File(p.replaceAll("\\\\", "/")).exists()) {
                    basePath = p;
                    break;
                }
            }
            this.basePath = basePath.replaceAll("\\\\", "/");
            return this;
        }


        public Builder attributes(Map<String,Object> clone) {
            attributes.putAll(clone);
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

    public String getContextPath() {
        return b.contextPath;
    }

    public int getMajorVersion() {
        return 3;
    }

    public int getMinorVersion() {
        return 0;
    }

    public String getMimeType(String file) {
        throw new UnsupportedOperationException();
    }

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

    public URL getResource(String path) throws MalformedURLException {
        if (!path.replace("\\\\", "/").startsWith("/")) {
            path = "/" + path;
        }
        return URI.create("file://" + b.basePath + path).toURL();
    }

    public InputStream getResourceAsStream(String path) {
        try {
            if (!path.replace("\\\\", "/").replace("\\", "/").startsWith("/")) {
                path = "/" + path;
            }

            File f = new File(path);
            if (!f.exists()) {
                f = new File(b.basePath + path);
            }

            return new FileInputStream(f);
        } catch (FileNotFoundException e) {
            logger.trace("", e);
        }
        return null;
    }

    public String getRealPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return b.basePath + path;
    }

    public String getServerInfo() {
        return "Nettosphere/3.0";
    }

    public String getInitParameter(String name) {
        return b.initParams.get(name);
    }

    public Enumeration getInitParameterNames() {
        return Collections.enumeration(b.initParams.values());
    }

    public Object getAttribute(String name) {
        return b.attributes.get(name);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(b.attributes.keySet());
    }

    public void setAttribute(String name, Object object) {
        b.attributes.put(name, object);
    }

    public void removeAttribute(String name) {
        b.attributes.remove(name);
    }

    public String getServletContextName() {
        return "Atmosphere";
    }

}

