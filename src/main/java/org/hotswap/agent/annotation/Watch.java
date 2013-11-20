package org.hotswap.agent.annotation;

import org.hotswap.agent.watch.WatchEvent;

import java.lang.annotation.*;

/**
 * Watch for a resource change.
 *
 * @author Jiri Bubnik
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Watch {

    /**
     * Prefix of resource path to watch.
     */
    String path();

    /**
     * Regexp expression to filter resources.
     */
    String filter() default "";

    /**
     * Filter watch event types. Default is all events (CREATE, MODIFY, DELETE).
     */
    WatchEvent.WatchEventType[] watchEvents() default {WatchEvent.WatchEventType.CREATE,
            WatchEvent.WatchEventType.MODIFY, WatchEvent.WatchEventType.DELETE};

    /**
     * Watch only for regular files. By default all other types (including directories) are filtered out.
     *
     * @return true to filter out other types than regular types.
     */
    public boolean onlyRegularFiles() default true;
}