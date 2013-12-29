package org.hotswap.agent.annotation.handler;

import java.lang.annotation.Annotation;

/**
 * Handler for an annotation on a plugin.
 * <p/>
 * There should exist a single handler class for each plugin annotation. Annotation processor than
 * use this interface to init fields and methods.
 *
 * @author Jiri Bubnik
 */
public interface PluginHandler<T extends Annotation> {

    /**
     * Initialization for field annotations.
     *
     * @param pluginAnnotation annotation values
     * @return true if initialized.
     */
    boolean initField(PluginAnnotation<T> pluginAnnotation);

    /**
     * Initialization for method annotations.
     *
     * @param pluginAnnotation annotation values
     * @return true if initialized.
     */
    boolean initMethod(PluginAnnotation<T> pluginAnnotation);

}
