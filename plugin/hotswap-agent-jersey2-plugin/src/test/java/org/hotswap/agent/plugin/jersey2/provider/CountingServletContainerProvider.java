/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.jersey2.provider;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.internal.spi.ServletContainerProvider;

/**
 * Registers a new binder on the config of each web component, as servlet container integrations do.
 */
public class CountingServletContainerProvider implements ServletContainerProvider {

    private static final AtomicInteger CONFIGURED = new AtomicInteger();

    @Override
    public void preInit(ServletContext servletContext, Set<Class<?>> classes) {
    }

    @Override
    public void postInit(ServletContext servletContext, Set<Class<?>> classes, Set<String> servletNames) {
    }

    @Override
    public void onRegister(ServletContext servletContext, Set<String> servletNames) {
    }

    @Override
    public void configure(ResourceConfig resourceConfig) {
        final ProviderState state = new ProviderState(CONFIGURED.incrementAndGet());
        resourceConfig.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(state).to(ProviderState.class);
            }
        });
    }
}
