package org.hotswap.agent.annotation;

import java.lang.annotation.*;

/**
 * Initialize plugin fields and methods.
 * <p/>
 * Non static fields and methods are set after the plugin instance is created and before any other method is invoked.
 * You can use this annotation to autowire agent services.
 * <p/>
 * Special use is @Init annotation on static method - then the method works as a callback after new classloader is
 * initialized in the plugin manager. @Init on static field just sets the service if applicable.
 * <p/>
 * Available method argument types:<ul>
 * <li>PluginManager - the single instance of plugin manager</li>
 * <li>Watcher - watcher service to register resource change listeners</li>
 * <li>Scheduler - schedule a command to run</li>
 * <li>HotswapTransformer - register class transformation</li>
 * <li>PluginConfiguration - access plugin configuration properties</li>
 * <li>ClassLoader - current application classloader (for static method on a field, this is the plugin classloader) </li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Init {
}
