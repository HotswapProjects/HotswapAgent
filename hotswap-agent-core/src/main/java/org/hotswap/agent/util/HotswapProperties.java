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
 *     extraClassPath=${extra.class.path}
 * jvm argument:
 *     -Dextra.class.path=/project_extra_class_path
 * result:
 *     extraClassPath=/project_extra_class_path
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
