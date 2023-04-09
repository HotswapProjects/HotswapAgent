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
package org.hotswap.agent.plugin.owb_jakarta.beans;

import java.util.Set;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;


/**
 * The Interface OwbHotswapContext.
 *
 * @author alpapad@gmail.com
 */
public interface OwbHotswapContext {

    /**
     * <p>
     * Destroy the existing contextual instance. If there is no existing
     * instance, no action is taken.
     * </p>
     *
     * @definedIn AlterableContext
     * @param contextual the contextual type
     * @throws ContextNotActiveException if the context is not active
     */
    void destroy(Contextual<?> contextual);

    /**
     * Determines if the context object is active.
     *
     * If the context is active, then $$ha$redefine() is called.
     *
     * @definedIn Context
     * @return <tt>true</tt> if the context is active, or <tt>false</tt>
     *         otherwise.
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
     * Adds a bean to the set of beans to be reloaded.
     *
     * @param bean
     */
    void $$ha$addBeanToReloadOwb(Contextual<Object> bean);

    /**
     * Gets the OWB beans to be reloaded.
     *
     * @return the OWB beans to be reloaded
     */
    Set<Contextual<Object>> $$ha$getBeansToReloadOwb();

    /**
     * reload the owb beans which have changed
     */
    void $$ha$reloadOwb();

    /**
     * the original isActive() method (renamed)
     *
     * @return
     */
    boolean $$ha$isActiveOwb();
}
