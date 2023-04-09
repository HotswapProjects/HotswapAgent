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
package org.hotswap.agent.plugin.undertow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hotswap.agent.logging.AgentLogger;
import org.xnio.IoUtils;

import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * Prefix Resource manager by extra class path, watch resources and webappdir
 */
public class PrefixingResourceManager implements ResourceManager {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PrefixingResourceManager.class);

    private List<ResourceManager> delegates;

    public PrefixingResourceManager(ResourceManager delegate) {
        this.delegates = new ArrayList<>();
        this.delegates.add(delegate);
    }

    public void setExtraResources(List extraResources) {
        List<ResourceManager> delegates = new ArrayList<>();
        for (Object o: extraResources) {
            File resource = File.class.cast(o);
            try {
                delegates.add(new FileResourceManager(resource.getCanonicalFile(), 1024, true, false, "/"));
            } catch (IOException e) {
                LOGGER.warning("Unable to create cannonical file from {}. File skipped.", resource.getName(), e);
            }
        }
        delegates.addAll(this.delegates);
        this.delegates = delegates;
    }

    @Override
    public Resource getResource(String path) throws IOException {
        for(ResourceManager d : delegates) {
            Resource res = d.getResource(path);
            if(res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        return true;
    }

    @Override
    public void registerResourceChangeListener(ResourceChangeListener listener) {
        for(ResourceManager del : delegates) {
            if(del.isResourceChangeListenerSupported()) {
                del.registerResourceChangeListener(listener);
            }
        }
    }

    @Override
    public void removeResourceChangeListener(ResourceChangeListener listener) {
        for(ResourceManager del : delegates) {
            if(del.isResourceChangeListenerSupported()) {
                del.removeResourceChangeListener(listener);
            }
        }
    }

    @Override
    public void close() throws IOException {
        for(ResourceManager del : delegates) {
            IoUtils.safeClose(del);
        }
    }
}
