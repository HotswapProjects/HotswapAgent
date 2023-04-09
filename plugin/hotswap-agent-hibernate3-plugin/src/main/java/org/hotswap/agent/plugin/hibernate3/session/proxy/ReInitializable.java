/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.io.File;
import java.net.URL;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;

/**
 * Interface applied to org.hibernate.cfg.Configuration. Investigate, if
 * ReInitializable and ReInitializableHelper can be turned into an abstract
 * class and made superclass of Configuration (which does not extend any class)
 *
 * @author alpapad@gmail.com
 *
 */
public interface ReInitializable {

    /**
     * Hot swap.
     */
    void hotSwap();

    /**
     * Re initialize.
     */
    void reInitialize();

    /**
     * Gets the override config.
     *
     * @return the override config
     */
    OverrideConfig getOverrideConfig();

    /**
     * Sets the property.
     *
     * @param propertyName
     *            the property name
     * @param value
     *            the value
     * @return the configuration
     */
    Configuration setProperty(String propertyName, String value);

    /**
     * Configure.
     *
     * @param resource
     *            the resource
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration configure(String resource) throws HibernateException;

    /**
     * Configure.
     *
     * @param url
     *            the url
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration configure(URL url) throws HibernateException;

    /**
     * Configure.
     *
     * @param configFile
     *            the config file
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration configure(File configFile) throws HibernateException;

    /**
     * Configure.
     *
     * @param document
     *            the document
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration configure(org.w3c.dom.Document document) throws HibernateException;

    /**
     * Configure.
     *
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration configure() throws HibernateException;

    /**
     * _build session factory.
     *
     * @return the org.hibernate. session factory
     * @throws HibernateException
     *             the hibernate exception
     */
    // Hiden..
    org.hibernate.SessionFactory _buildSessionFactory() throws org.hibernate.HibernateException;

    /**
     * _set property.
     *
     * @param propertyName
     *            the property name
     * @param value
     *            the value
     * @return the configuration
     */
    Configuration _setProperty(String propertyName, String value);

    /**
     * _configure.
     *
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration _configure() throws HibernateException;

    /**
     * _configure.
     *
     * @param resource
     *            the resource
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration _configure(String resource) throws HibernateException;

    /**
     * _configure.
     *
     * @param url
     *            the url
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration _configure(URL url) throws HibernateException;

    /**
     * _configure.
     *
     * @param configFile
     *            the config file
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration _configure(File configFile) throws HibernateException;

    /**
     * _configure.
     *
     * @param document
     *            the document
     * @return the configuration
     * @throws HibernateException
     *             the hibernate exception
     */
    Configuration _configure(org.w3c.dom.Document document) throws HibernateException;
}
