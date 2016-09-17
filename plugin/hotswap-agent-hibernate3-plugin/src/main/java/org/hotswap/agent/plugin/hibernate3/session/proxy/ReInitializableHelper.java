/*
 * Copyright 2016 the original author or authors.
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
package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.io.File;
import java.net.URL;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig.ConfiguredBy;

/**
 * Workaround for not being able to use java8 interfaces with default methods...
 * Oh well...
 * 
 * @author alpapad@gmail.com
 *
 */
public class ReInitializableHelper {

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(ReInitializableHelper.class);

    /**
     * Hot swap.
     *
     * @param r
     *            the r
     */
    public static void hotSwap(ReInitializable r) {

        OverrideConfig o = r.getOverrideConfig();
        r.reInitialize();
        switch (o.configuredBy) {
        case FILE:
            r.configure(File.class.cast(o.config));
            break;
        case NONE:
            r.configure();
            break;
        case STRING:
            r.configure(String.class.cast(o.config));
            break;
        case URL:
            r.configure(URL.class.cast(o.config));
            break;
        case W3C:
            r.configure(org.w3c.dom.Document.class.cast(o.config));
            break;
        default:
            throw new RuntimeException("Don't know how to reconficure...");
        }
        for (Map.Entry<String, String> e : o.properties.entrySet()) {
            r.setProperty(e.getKey(), e.getValue());
        }
    }

    /**
     * Sets the property.
     *
     * @param r
     *            the r
     * @param propertyName
     *            the property name
     * @param value
     *            the value
     * @return the configuration
     */
    public static Configuration setProperty(ReInitializable r, String propertyName, String value) {
        LOGGER.debug("setProperty..................... key:" + propertyName + ", value:" + value);
        r._setProperty(propertyName, value);
        r.getOverrideConfig().properties.put(propertyName, value);
        return (Configuration) r;
    }

    /**
     * Configure.
     *
     * @param r
     *            the r
     * @param resource
     *            the resource
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    public static Configuration configure(ReInitializable r, String resource) throws HibernateException {
        LOGGER.debug("Configuring....................." + resource);
        r._configure(resource);
        r.getOverrideConfig().config = resource;
        r.getOverrideConfig().configuredBy = ConfiguredBy.STRING;
        r.getOverrideConfig().properties.clear();
        return (Configuration) r;
    }

    /**
     * Configure.
     *
     * @param r
     *            the r
     * @param url
     *            the url
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    public static Configuration configure(ReInitializable r, java.net.URL url) throws HibernateException {
        LOGGER.debug("Configuring....................." + url);
        r._configure(url);
        r.getOverrideConfig().config = url;
        r.getOverrideConfig().configuredBy = ConfiguredBy.URL;
        r.getOverrideConfig().properties.clear();
        return (Configuration) r;
    }

    /**
     * Configure.
     *
     * @param r
     *            the r
     * @param configFile
     *            the config file
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    public static Configuration configure(ReInitializable r, java.io.File configFile) throws HibernateException {
        System.err.println("Configuring....................." + configFile);
        r._configure(configFile);
        r.getOverrideConfig().properties.clear();
        return (Configuration) r;
    }

    /**
     * Configure.
     *
     * @param r
     *            the r
     * @param document
     *            the document
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    public static Configuration configure(ReInitializable r, org.w3c.dom.Document document) throws HibernateException {
        LOGGER.debug("Configuring....................." + document);
        r._configure(document);
        r.getOverrideConfig().config = document;
        r.getOverrideConfig().configuredBy = ConfiguredBy.W3C;
        r.getOverrideConfig().properties.clear();
        return (Configuration) r;
    }

    /**
     * Configure.
     *
     * @param r
     *            the r
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    public static Configuration configure(ReInitializable r) throws HibernateException {
        LOGGER.debug("Configuring..................... EMPTY..");
        r._configure();
        r.getOverrideConfig().config = null;
        r.getOverrideConfig().configuredBy = ConfiguredBy.NONE;
        r.getOverrideConfig().properties.clear();
        return (Configuration) r;
    }

}
