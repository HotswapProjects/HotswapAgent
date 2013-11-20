package org.hotswap.agent.annotation.handler;

import java.lang.annotation.Annotation;

/**
 * Handler for annotation on a plugin.
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
