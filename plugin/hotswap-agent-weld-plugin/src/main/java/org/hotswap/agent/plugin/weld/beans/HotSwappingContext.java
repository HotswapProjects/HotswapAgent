package org.hotswap.agent.plugin.weld.beans;


import java.util.Set;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Contextual;


/**
 * The Interface HotSwappingContext.
 *
 * @author alpapad@gmail.com
 */
public interface HotSwappingContext {

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
     * If the context is active, then _redefine() is called.
     *
     * @definedIn Context
     *
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
     * Adds a bean to the set of beans to be redefined.
     *
     * @param bean
     */
    void addBean(Contextual<Object> bean);

    Set<Contextual<Object>> getBeans();

    /** Actually private methods... */
    /**
     * redefines the beans which have changed
     */
    void _redefine();

    /**
     * the original isActive() method (renamed)
     *
     * @return
     */
    boolean _isActive();
}
