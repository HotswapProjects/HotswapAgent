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
package org.hotswap.agent.plugin.jersey2.instance;

import java.util.Collections;
import java.util.Set;

import javax.ws.rs.core.Application;

/**
 * Given to the container as an instance wrapped by ResourceConfig.forApplication(). If Jersey created it again from
 * its class on reload, its state would be "recreated".
 */
public class InstanceApplication extends Application {

    private final String state;

    public InstanceApplication() {
        this("recreated");
    }

    public InstanceApplication(String state) {
        this.state = state;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return Collections.<Class<?>>singleton(InstanceResource.class);
    }

    @Override
    public Set<Object> getSingletons() {
        return Collections.<Object>singleton(new StateBinder(state));
    }
}
