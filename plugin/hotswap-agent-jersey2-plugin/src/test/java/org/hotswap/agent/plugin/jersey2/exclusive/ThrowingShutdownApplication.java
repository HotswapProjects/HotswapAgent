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
package org.hotswap.agent.plugin.jersey2.exclusive;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;

/**
 * Throws when it is shut down.
 */
public class ThrowingShutdownApplication extends ResourceConfig {

    public ThrowingShutdownApplication() {
        register(ThrowingShutdownResource.class);
        register(new ContainerLifecycleListener() {
            @Override
            public void onStartup(Container container) {
            }

            @Override
            public void onReload(Container container) {
            }

            @Override
            public void onShutdown(Container container) {
                throw new IllegalStateException("ThrowingShutdownApplication cannot be shut down");
            }
        });
    }
}
