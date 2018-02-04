package org.hotswap.agent.plugin.deltaspike.context;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import org.apache.deltaspike.core.api.scope.GroupedConversationScoped;
import org.apache.deltaspike.core.impl.scope.window.WindowContextImpl;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.transformer.DeltaspikeContextsTransformer;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class GroupedConversationContextTracker. Simple expose grouped conversation contexts in window scope
 *
 * @author Vladimir Dvorak
 */
public class GroupedConversationContextTracker implements Iterable, Serializable {

    private static final long serialVersionUID = 6453857829741289137L;

    private static AgentLogger LOGGER = AgentLogger.getLogger(GroupedConversationContextTracker.class);

    public static class GroupedConversationIterator implements Iterator<Object> {

        private boolean first = true;

        @Override
        public boolean hasNext() {
            return first;
        }

        @Override
        public Object next() {
            first = false;
            return null;
        }

        @Override
        public void remove() {
        }
    }

    @Override
    public Iterator<Object> iterator() {
        // assume window context is set , simply return grouped iterator
        return new GroupedConversationIterator();
    }

    /**
     * Register to current session's tracker field
     */
    public static void register(WindowContextImpl windowContext) {
        try {
            Map m = (Map) ReflectionHelper.get(windowContext, DeltaspikeContextsTransformer.CUSTOM_CONTEXT_TRACKER_FIELD);
            if (!m.containsKey(GroupedConversationScoped.class.getName())) {
                m.put(GroupedConversationScoped.class.getName(), new GroupedConversationContextTracker());
                LOGGER.debug("GroupedConversationContextTracker added to context '{}'", windowContext);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("Field '{}' not found in context class '{}'.", DeltaspikeContextsTransformer.CUSTOM_CONTEXT_TRACKER_FIELD,
                    windowContext.getClass().getName());
        }
    }
}
