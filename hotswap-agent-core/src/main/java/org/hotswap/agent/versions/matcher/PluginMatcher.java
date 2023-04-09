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
package org.hotswap.agent.versions.matcher;

import org.hotswap.agent.annotation.Versions;

/**
 * The PluginMatcher is the matcher responsible for parsing and applying the
 * matching algorithm at the plugin level (plugin level annotations). The
 * application of the rules is still evaluated per method call
 *
 * @author alpapad@gmail.com
 */
public class PluginMatcher extends AbstractMatcher {

    /**
     * Instantiates a new plugin matcher.
     *
     * @param pluginClass
     *            the plugin class
     */
    public PluginMatcher(Class<?> pluginClass) {
        super(pluginClass.getAnnotation(Versions.class));
    }

}
