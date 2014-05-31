package org.hotswap.agent.annotation;

import org.hotswap.agent.watch.WatchEvent;

import java.lang.annotation.*;

/**
 * Watch for a resource change.
 * <p/>
 * Use with a non static method.
 * <p/>
 * Method attribute types:<ul>
 * <li>URI - URI of the watched resource</li>
 * <li>URL - URL of the watched resource</li>
 * <li>ClassLoader - the application classloader</li>
 * <li>CtClass - javassist CtClass created from target file (use with .class filter)</li>
 * <li>ClassPool - initialized javassist classpool</li>
 * </ul>
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

    /**
     * Merge multiple same watch events up to this timeout into a single watch event (useful to merge multiple MODIFY events).
     */
    public int timeout() default 50;
}