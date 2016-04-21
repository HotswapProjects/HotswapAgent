package org.hotswap.agent.annotation;

import java.lang.annotation.*;

/**
 * Plugin definition.
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Plugin {

    /**
     * A name of the plugin. This name is used to reference the plugin in code and configuration. It should not
     * contain any spaces and weird characters.
     *
     * @return A name of the plugin
     */
    String name() default "";

    /**
     * Any meaningful plugin description.
     */
    String description() default "";

    /**
     * Version of target framework this framework was tested with.
     */
    String[] testedVersions();

    /**
     * Version of target framework this framework should work with. It is not possible to test every possible framework
     * version for all plugins. Because the plugin is usually hooked to a stable framework structure, it should
     * for all subversions of a major version. Indicate with this property expected versions.
     */
    String[] expectedVersions() default {};

    /**
     * Split plugin definition into multiple class files. Annotations @OnClassLoadEvent and @OnResourceFileEvent will be scanned on
     * supporting class in addition to pluginClass itself.
     */
    Class<?>[] supportClass() default {};
}
