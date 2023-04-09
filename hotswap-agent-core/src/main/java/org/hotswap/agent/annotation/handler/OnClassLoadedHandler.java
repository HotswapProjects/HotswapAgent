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
package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;

/**
 * Transform method handler - handle @OnClassLoadEvent annotation on a method.
 *
 * @author Jiri Bubnik
 */
public class OnClassLoadedHandler implements PluginHandler<OnClassLoadEvent> {
    protected static AgentLogger LOGGER = AgentLogger.getLogger(OnClassLoadedHandler.class);

    protected PluginManager pluginManager;

    protected HotswapTransformer hotswapTransformer;

    public OnClassLoadedHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        this.hotswapTransformer = pluginManager.getHotswapTransformer();

        if (hotswapTransformer == null) {
            throw new IllegalArgumentException("Error instantiating OnClassLoadedHandler. Hotswap transformer is missing in PluginManager.");
        }
    }

    @Override
    public boolean initField(PluginAnnotation<OnClassLoadEvent> pluginAnnotation) {
        throw new IllegalAccessError("@OnClassLoadEvent annotation not allowed on fields.");
    }

    @Override
    public boolean initMethod(final PluginAnnotation<OnClassLoadEvent> pluginAnnotation) {
        LOGGER.debug("Init for method " + pluginAnnotation.getMethod());

        if (hotswapTransformer == null) {
            LOGGER.error("Error in init for method " + pluginAnnotation.getMethod() + ". Hotswap transformer is missing.");
            return false;
        }

        final OnClassLoadEvent annot = pluginAnnotation.getAnnotation();

        if (annot == null) {
            LOGGER.error("Error in init for method " + pluginAnnotation.getMethod() + ". Annotation missing.");
            return false;
        }

        ClassLoader appClassLoader = null;
        if (pluginAnnotation.getPlugin() != null){
            appClassLoader = pluginManager.getPluginRegistry().getAppClassLoader(pluginAnnotation.getPlugin());
        }

        hotswapTransformer.registerTransformer(appClassLoader, annot.classNameRegexp(), new PluginClassFileTransformer(pluginManager, pluginAnnotation));
        return true;
    }
}
