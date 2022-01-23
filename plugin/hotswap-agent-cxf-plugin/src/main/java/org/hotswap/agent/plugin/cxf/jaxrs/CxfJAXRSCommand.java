/*
 * Copyright 2013-2022 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.cxf.jaxrs;

import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

/**
 * The Class CxfJAXRSCommand.
 */
public class CxfJAXRSCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CxfJAXRSCommand.class);

    private ClassLoader classLoader;
    private ClassResourceInfo criProxy;

    public void setupCmd(ClassLoader classLoader, Object criProxy) {
        this.classLoader = classLoader;
        this.criProxy = (ClassResourceInfo) criProxy;
    }

    @Override
    public void executeCommand() {

        LOGGER.debug("Reloading service={}, in classLoader={}", criProxy.getServiceClass(), classLoader);

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            ClassResourceInfoProxyHelper.reloadClassResourceInfo(criProxy);
            LOGGER.info("Resource class {} reloaded.", criProxy.getResourceClass().getName());
        } catch (Exception e) {
            LOGGER.error("Could not reload JAXRS service class {}", e, criProxy.getServiceClass());
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((classLoader == null) ? 0 : classLoader.hashCode());
        result = prime * result + ((criProxy == null) ? 0 : criProxy.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CxfJAXRSCommand other = (CxfJAXRSCommand) obj;

        if (classLoader == null) {
            if (other.classLoader != null)
                return false;
        } else if (!classLoader.equals(other.classLoader))
            return false;
        if (criProxy == null) {
            if (other.criProxy != null)
                return false;
        } else if (!criProxy.equals(other.criProxy))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CxfJAXRSCommand[classLoader=" + classLoader + ", service class =" + criProxy.getServiceClass() + "]";
    }
}