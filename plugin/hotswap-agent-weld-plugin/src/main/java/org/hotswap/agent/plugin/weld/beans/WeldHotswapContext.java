package org.hotswap.agent.plugin.weld.beans;


import java.util.Set;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Contextual;


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
     * If the context is active, then _reload() is called.
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
    void _addBeanToReloadWeld(Contextual<Object> bean);

    /**
     * Gets the Weld beans to be reloaded. The naming must be Weld-specific since OWB plugin patches the contexts as well.
     *
     * @return the Weld beans to be reloaded
     */
    Set<Contextual<Object>> _getBeansToReloadWeld();

    /**
     * Redefines the beans which have changed. The naming must be Weld-specific since OWB plugin patches the contexts as well.
     */
    void _reloadWeld();

    /**
     * The original isActive() method (renamed). The naming must be Weld-specific since OWB plugin patches the contexts as well.
     *
     * @return
     */
    boolean _isActiveWeld();
}
