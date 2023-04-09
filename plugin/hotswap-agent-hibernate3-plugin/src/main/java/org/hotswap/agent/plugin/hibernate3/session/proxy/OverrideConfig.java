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
package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plugin configuration.
 *
 * @author alpapad@gmail.com
 */
public class OverrideConfig {

    /**
     * The Enum ConfiguredBy.
     */
    public static enum ConfiguredBy {

        /** The none. */
        NONE,

        /** The file. */
        FILE,

        /** The string. */
        STRING,

        /** The url. */
        URL,

        /** The W3 c. */
        W3C
    }

    /** The configured by. */
    public ConfiguredBy configuredBy = ConfiguredBy.NONE;

    /** The config. */
    public Object config = null;

    /** The properties. */
    public Map<String, String> properties = new LinkedHashMap<>();

}
