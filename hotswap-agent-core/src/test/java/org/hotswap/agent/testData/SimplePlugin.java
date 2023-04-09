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
package org.hotswap.agent.testData;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.OnClassLoadEvent;

/**
 * Created by bubnik on 11.10.13.
 */
@Plugin(name = "Hibernate plugin", testedVersions = {"1.0"})
public class SimplePlugin {

    @Init
    public void initPlugin() {
    }

    @Init
    public void initPlugin(PluginManager manager) {
    }


    @OnClassLoadEvent(classNameRegexp = "org.hotswap.example.type")
    public void transform() {

    }

    // used by PluginManagerInvokerTest to dynamically call this method.
    public void callPluginMethod(Boolean val) {
    }
}
