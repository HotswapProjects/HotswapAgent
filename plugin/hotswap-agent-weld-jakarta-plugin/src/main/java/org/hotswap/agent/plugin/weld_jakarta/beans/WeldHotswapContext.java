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
package org.hotswap.agent.plugin.weld_jakarta.beans;


import java.util.Set;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;


/**
 * The Interface WeldHotswapContext.
 *
 * @author alpapad@gmail.com
 */
public interface WeldHotswapContext {

    /**
     * Destroy the existing contextual instance. If there is no existing
     * instance, no action is taken.
     *
     * @definedIn AlterableContext
     * @param contextual the contextual type
     * @throws ContextNotActiveException if the context is not active
     */
    void destroy(Contextual<?> contextual);

    /**
     * Determines if the context object is active.
     * If the context is active, then $$ha$reload() is called.
     *
     * @return <tt>true</tt> if the context is active, or <tt>false</tt>
     *         otherwise.
     * @definedIn Context
     */
    boolean isActive();

    /**
     * Return an existing instance of a certain contextual type or a null value.
     *
     * @param <T> the type of the contextual type
     * @param contextual the contextual type
     * @return the contextual instance, or a null value
     *
     * @throws ContextNotActiveException if the context is not active
     */
    public <T> T get(Contextual<T> contextual);

    /**
     * Adds a bean to the set of beans to be redefined.
     *
     * @param bean
     */
    void $$ha$addBeanToReloadWeld(Contextual<Object> bean);

    /**
     * Gets the Weld beans to be reloaded. The naming must be Weld-specific since OWB plugin patches the contexts as well.
     *
     * @return the Weld beans to be reloaded
     */
    Set<Contextual<Object>> $$ha$getBeansToReloadWeld();

    /**
     * Redefines the beans which have changed. The naming must be Weld-specific since OWB plugin patches the contexts as well.
     */
    void $$ha$reloadWeld();

    /**
     * The original isActive() method (renamed). The naming must be Weld-specific since OWB plugin patches the contexts as well.
     *
     * @return
     */
    boolean $$ha$isActiveWeld();
}
