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
package org.hotswap.agent.plugin.jersey2.cdi;

import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;

import org.glassfish.jersey.server.ResourceConfig;
import org.hotswap.agent.plugin.jersey2.instance.StateBinder;

/**
 * A CDI bean, which Jersey would get from CDI as the same instance instead of creating it again. Each instance binds
 * its number as the state.
 */
@ApplicationScoped
public class CdiApplication extends ResourceConfig {

    private static final AtomicInteger INSTANCES = new AtomicInteger();

    public CdiApplication() {
        register(CdiResource.class);
        register(new StateBinder("instance " + INSTANCES.incrementAndGet()));
    }
}
