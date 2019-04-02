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
