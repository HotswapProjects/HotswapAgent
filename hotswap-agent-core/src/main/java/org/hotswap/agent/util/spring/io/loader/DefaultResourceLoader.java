/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hotswap.agent.util.spring.io.loader;

import java.net.MalformedURLException;
import java.net.URL;

import org.hotswap.agent.util.spring.io.resource.ClassPathResource;
import org.hotswap.agent.util.spring.io.resource.ContextResource;
import org.hotswap.agent.util.spring.io.resource.Resource;
import org.hotswap.agent.util.spring.io.resource.UrlResource;
import org.hotswap.agent.util.spring.util.Assert;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.hotswap.agent.util.spring.util.StringUtils;

/**
 * Default implementation of the {@link ResourceLoader} interface. Used by
 * {@link ResourceEditor}, and serves as base class for
 * {@link org.springframework.context.support.AbstractApplicationContext}. Can
 * also be used standalone.
 *
 * <p>
 * Will return a {@link UrlResource} if the location value is a URL, and a
 * {@link ClassPathResource} if it is a non-URL path or a "classpath:"
 * pseudo-URL.
 *
 * @author Juergen Hoeller
 * @since 10.03.2004
 * @see FileSystemResourceLoader
 * @see org.springframework.context.support.ClassPathXmlApplicationContext
 */
public class DefaultResourceLoader implements ResourceLoader {

    private ClassLoader classLoader;

    /**
     * Create a new DefaultResourceLoader.
     * <p>
     * ClassLoader access will happen using the thread context class loader at
     * the time of this ResourceLoader's initialization.
     * 
     * @see java.lang.Thread#getContextClassLoader()
     */
    public DefaultResourceLoader() {
        this.classLoader = ClassUtils.getDefaultClassLoader();
    }

    /**
     * Create a new DefaultResourceLoader.
     * 
     * @param classLoader
     *            the ClassLoader to load class path resources with, or
     *            {@code null} for using the thread context class loader at the
     *            time of actual resource access
     */
    public DefaultResourceLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Specify the ClassLoader to load class path resources with, or
     * {@code null} for using the thread context class loader at the time of
     * actual resource access.
     * <p>
     * The default is that ClassLoader access will happen using the thread
     * context class loader at the time of this ResourceLoader's initialization.
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Return the ClassLoader to load class path resources with.
     * <p>
     * Will get passed to ClassPathResource's constructor for all
     * ClassPathResource objects created by this resource loader.
     * 
     * @see ClassPathResource
     */
    @Override
    public ClassLoader getClassLoader() {
        return (this.classLoader != null ? this.classLoader : ClassUtils.getDefaultClassLoader());
    }

    @Override
    public Resource getResource(String location) {
        Assert.notNull(location, "Location must not be null");
        if (location.startsWith("/")) {
            return getResourceByPath(location);
        } else if (location.startsWith(CLASSPATH_URL_PREFIX)) {
            return new ClassPathResource(location.substring(CLASSPATH_URL_PREFIX.length()), getClassLoader());
        } else {
            try {
                // Try to parse the location as a URL...
                URL url = new URL(location);
                return new UrlResource(url);
            } catch (MalformedURLException ex) {
                // No URL -> resolve as resource path.
                return getResourceByPath(location);
            }
        }
    }

    /**
     * Return a Resource handle for the resource at the given path.
     * <p>
     * The default implementation supports class path locations. This should be
     * appropriate for standalone implementations but can be overridden, e.g.
     * for implementations targeted at a Servlet container.
     * 
     * @param path
     *            the path to the resource
     * @return the corresponding Resource handle
     * @see ClassPathResource
     * @see org.springframework.context.support.FileSystemXmlApplicationContext#getResourceByPath
     * @see org.springframework.web.context.support.XmlWebApplicationContext#getResourceByPath
     */
    protected Resource getResourceByPath(String path) {
        return new ClassPathContextResource(path, getClassLoader());
    }

    /**
     * ClassPathResource that explicitly expresses a context-relative path
     * through implementing the ContextResource interface.
     */
    protected static class ClassPathContextResource extends ClassPathResource implements ContextResource {

        public ClassPathContextResource(String path, ClassLoader classLoader) {
            super(path, classLoader);
        }

        @Override
        public String getPathWithinContext() {
            return getPath();
        }

        @Override
        public Resource createRelative(String relativePath) {
            String pathToUse = StringUtils.applyRelativePath(getPath(), relativePath);
            return new ClassPathContextResource(pathToUse, getClassLoader());
        }
    }

}