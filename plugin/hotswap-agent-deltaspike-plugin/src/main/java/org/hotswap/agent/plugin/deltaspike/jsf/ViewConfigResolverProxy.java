package org.hotswap.agent.plugin.deltaspike.jsf;

import java.util.List;

import org.apache.deltaspike.core.api.config.view.ViewConfig;
import org.apache.deltaspike.core.api.config.view.metadata.ConfigDescriptor;
import org.apache.deltaspike.core.api.config.view.metadata.ViewConfigDescriptor;
import org.apache.deltaspike.core.api.config.view.metadata.ViewConfigResolver;

public class ViewConfigResolverProxy implements ViewConfigResolver {

    private ViewConfigResolver viewConfigResolver;

    public void setViewConfigResolver(Object viewConfigResolver) {
        this.viewConfigResolver = (ViewConfigResolver) viewConfigResolver;
    }

    @Override
    public ConfigDescriptor<?> getConfigDescriptor(String path) {
        return viewConfigResolver.getConfigDescriptor(path);
    }

    @Override
    public ConfigDescriptor<?> getConfigDescriptor(Class<?> configClass) {
        return viewConfigResolver.getConfigDescriptor(configClass);
    }

    @Override
    public List<ConfigDescriptor<?>> getConfigDescriptors() {
        return viewConfigResolver.getConfigDescriptors();
    }

    @Override
    public ViewConfigDescriptor getViewConfigDescriptor(String viewId) {
        return viewConfigResolver.getViewConfigDescriptor(viewId);
    }

    @Override
    public ViewConfigDescriptor getViewConfigDescriptor(Class<? extends ViewConfig> viewDefinitionClass) {
        return viewConfigResolver.getViewConfigDescriptor(viewDefinitionClass);
    }

    @Override
    public List<ViewConfigDescriptor> getViewConfigDescriptors() {
        return viewConfigResolver.getViewConfigDescriptors();
    }

    @Override
    public ViewConfigDescriptor getDefaultErrorViewConfigDescriptor() {
        return viewConfigResolver.getDefaultErrorViewConfigDescriptor();
    }

}
