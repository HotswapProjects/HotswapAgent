package org.hotswap.agent.plugin.cdi;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Helper class for common names definition for CDI plugins
 */
public class HaCdiCommons {

    private static AgentLogger LOGGER = AgentLogger.getLogger(HaCdiCommons.class);

    public static final String CUSTOM_CONTEXT_TRACKER_FIELD = "$$ha$customContextTrackers";
    public static final String ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD = "$$ha$attachCustomContextTracker";
    public static final String HA_DELEGATE = "$$ha$delegate";

    private static final Set<String> TRACKABLE_SESSION_BASED_SCOPES = new HashSet<>();

    static {
        TRACKABLE_SESSION_BASED_SCOPES.add("javax.enterprise.context.SessionScoped");
        TRACKABLE_SESSION_BASED_SCOPES.add("org.apache.deltaspike.core.api.scope.WindowScoped");
        TRACKABLE_SESSION_BASED_SCOPES.add("org.apache.deltaspike.core.api.scope.GroupedConversationScoped");
        TRACKABLE_SESSION_BASED_SCOPES.add("org.omnifaces.cdi.ViewScoped");
    }

    /**
     * Return true if context can be tracked usin session tracking mecahnism in Owb/Weld plugins
     *
     * @param scopeClass the scope class
     * @return true, if is trackable scope
     */
    public static boolean isTrackableScope(Class<?> scopeClass) {
        return TRACKABLE_SESSION_BASED_SCOPES.contains(scopeClass.getName());
    }

    /**
     * Gets the session context.
     *
     * @return the session context
     */
    public static Context getSessionContext() {
        BeanManager beanManager = CDI.current().getBeanManager();
        Context context = null;
        try {
            context = beanManager.getContext(SessionScoped.class);
            Context delegate = (Context) ReflectionHelper.invoke(context, context.getClass(), HA_DELEGATE, null);
            if (delegate != null && delegate != context) {
                context = delegate;
            }
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            LOGGER.error("Delegate retrieving failed.", e);
        }
        return context;
    }

}
