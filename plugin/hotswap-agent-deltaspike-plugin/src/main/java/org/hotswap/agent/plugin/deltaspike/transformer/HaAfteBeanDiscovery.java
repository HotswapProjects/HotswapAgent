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
package org.hotswap.agent.plugin.deltaspike.transformer;

import java.util.List;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;

import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class HaAfteBeanDiscovery - register repository classes from RepositoryExtension to DeltaspikePlugin in afterBeanDiscovery
 */
public class HaAfteBeanDiscovery {

    public void $$ha$afterBeanDiscovery(@Observes AfterBeanDiscovery before) {
        PluginManagerInvoker.callPluginMethod(DeltaSpikePlugin.class, getClass().getClassLoader(),
                "registerRepositoryClasses", new Class[] { List.class }, new Object[] { ReflectionHelper.get(this, "repositoryClasses") });
    }

}
