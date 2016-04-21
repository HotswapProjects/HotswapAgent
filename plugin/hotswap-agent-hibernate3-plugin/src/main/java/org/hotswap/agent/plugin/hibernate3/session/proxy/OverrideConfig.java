/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
