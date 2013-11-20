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

    String name() default "";

    String description() default "";

    String[] testedVersions();

    String[] expectedVersions() default {};
}
