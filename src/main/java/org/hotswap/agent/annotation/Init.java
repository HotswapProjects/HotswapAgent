package org.hotswap.agent.annotation;

import java.lang.annotation.*;

/**
 * Initialize this plugin, you can specify one or more parameters.
 * <p/>
 * Available method argument types:<ul>
 * <li>PluginManager - the single instance of plugin manager</li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Init {
}
