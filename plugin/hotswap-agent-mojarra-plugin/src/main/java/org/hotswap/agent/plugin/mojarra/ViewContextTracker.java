package org.hotswap.agent.plugin.mojarra;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import javax.enterprise.context.spi.Context;
import javax.faces.view.ViewScoped;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Iterate over all WindowContexts in current session context
 *
 * @author Vladimir Dvorak
 */
public class ViewContextTracker implements Iterable, Serializable {

    private static final long serialVersionUID = 1L;

    private static AgentLogger LOGGER = AgentLogger.getLogger(ViewContextTracker.class);

    public static class ViewContextsIterator implements Iterator<Object>, Closeable {

        private int index = 0;

        public ViewContextsIterator() {
            // TODO:
        }

        @Override
        public boolean hasNext() {
            // TODO:
            return index < 0;
        }

        @Override
        public Object next() {
            // TODO:
            return null;
        }

        @Override
        public void remove() {
            // TODO:
        }

        @Override
        public void close() throws IOException {
            // TODO:
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new ViewContextsIterator();
    }

    /**
     * Register to current session's tracker field
     */
    public static void register() {
        Context context = HaCdiCommons.getSessionContext();
        if (context != null) {
            attach(context);
        } else {
            LOGGER.error("No session context");
        }
    }

    /**
     * Attach to tracker field in session context
     *
     * @param context the context
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void attach(Object context) {
        try {
            Map m = (Map) ReflectionHelper.get(context, HaCdiCommons.CUSTOM_CONTEXT_TRACKER_FIELD);
            if (!m.containsKey(ViewScoped.class.getName())) {
                m.put(ViewScoped.class.getName(), new ViewContextTracker());
                LOGGER.debug("ViewContextTracker added to context '{}'", context);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("Field '{}' not found in context class '{}'.", HaCdiCommons.CUSTOM_CONTEXT_TRACKER_FIELD,
                    context.getClass().getName());
        }
    }
}
