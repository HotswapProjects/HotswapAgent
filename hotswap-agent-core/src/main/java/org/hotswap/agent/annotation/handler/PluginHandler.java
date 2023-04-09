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
