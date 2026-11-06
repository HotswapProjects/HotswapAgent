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
package org.hotswap.agent.plugin.jersey2.swap;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;

import org.hotswap.agent.plugin.jersey2.app.Greeter;
import org.hotswap.agent.plugin.jersey2.provider.ProviderState;

/**
 * Swapped for {@link org.hotswap.agent.plugin.jersey2.app.GreetingResource}. The added resource method is found only
 * after Jersey reloads the container.
 */
@Path("greeting")
public class GreetingResource2 {

    @Inject
    Greeter greeter;

    @Inject
    ProviderState providerState;

    @Context
    Configuration configuration;

    @GET
    public String greet() {
        return greeter.greet() + " v2";
    }

    @GET
    @Path("added")
    public String added() {
        return greeter.greet() + " " + configuration.getProperty("greeting.property") + " added";
    }

    @GET
    @Path("provider")
    public String provider() {
        return "provider " + providerState.getNumber();
    }
}
