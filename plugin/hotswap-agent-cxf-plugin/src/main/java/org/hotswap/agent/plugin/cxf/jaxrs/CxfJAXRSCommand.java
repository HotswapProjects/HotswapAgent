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
package org.hotswap.agent.plugin.cxf.jaxrs;

import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

import java.util.Objects;

/**
 * The Class CxfJAXRSCommand.
 */
public class CxfJAXRSCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CxfJAXRSCommand.class);

    private ClassLoader classLoader;
    private ClassResourceInfo criProxy;
    private String resourceClassPath;

    public void setupCmd(ClassLoader classLoader, Object criProxy) {
        this.classLoader = classLoader;
        this.criProxy = (ClassResourceInfo) criProxy;
        resourceClassPath = this.criProxy.getServiceClass().toString();
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

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CxfJAXRSCommand that = (CxfJAXRSCommand) object;
        return Objects.equals(resourceClassPath, that.resourceClassPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceClassPath);
    }

    @Override
    public String toString() {
        return "CxfJAXRSCommand[classLoader=" + classLoader + ", service class =" + criProxy.getServiceClass() + "]";
    }
}