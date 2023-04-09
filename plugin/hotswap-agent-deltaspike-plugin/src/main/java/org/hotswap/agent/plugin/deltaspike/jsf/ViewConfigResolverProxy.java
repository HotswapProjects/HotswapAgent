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
package org.hotswap.agent.plugin.deltaspike.jsf;

import java.util.List;

import org.apache.deltaspike.core.api.config.view.ViewConfig;
import org.apache.deltaspike.core.api.config.view.metadata.ConfigDescriptor;
import org.apache.deltaspike.core.api.config.view.metadata.ViewConfigDescriptor;
import org.apache.deltaspike.core.api.config.view.metadata.ViewConfigResolver;

/**
 * ViewConfigResolverProxy.
 *
 * @author Vladimir Dvorak
 */
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
