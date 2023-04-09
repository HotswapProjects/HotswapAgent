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
package org.hotswap.agent.util;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HotswapAgent Properties implementation.
 * <p/>
 * Scan property values for system variable reference in form of ${sys_var_xy} and replace it with appropriate
 * system variable value
 *
 * example:
 * hotswap-agent.properties:
 *     extraClasspath=${extra.class.path}
 * jvm argument:
 *     -Dextra.class.path=/project_extra_class_path
 * result:
 *     extraClasspath=/project_extra_class_path
 *
 */
public class HotswapProperties extends Properties {

    private static final long serialVersionUID = 4467598209091707788L;

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9._]+?)\\}");

    @Override
    public Object put(Object key, Object value) {
        return super.put(key, substitute(value));
    }

    private Object substitute(Object obj) {
        if (obj instanceof String) {
            StringBuffer result = new StringBuffer();
            Matcher m = VAR_PATTERN.matcher((String) obj);
            while (m.find()) {
                String replacement = System.getProperty(m.group(1));
                if (replacement != null) {
                    m.appendReplacement(result, replacement);
                }
            }
            m.appendTail(result);
            return result.toString();
        }

        return obj;
    }

}
