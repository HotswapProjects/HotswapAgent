package org.hotswap.agent.plugin.owb.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.webbeans.context.SessionContext;

/**
 * The Class SessionContextsTracker. Keep map of active session contexts and supply iterator over them
 *
 * @author Vladimir Dvorak
 */
public class SessionContextsTracker implements Iterable<Object> {

    public class SessionContextsIterator implements Iterator<Object> {

        private int index = 0;
        private List<SessionContext> sessionContextsList;

        public SessionContextsIterator(List<SessionContext> list) {
           sessionContextsList = new ArrayList<>();
            for (SessionContext ctx : list) {
                if (ctx.isActive()) {
                    sessionContextsList.add(ctx);
                }
            }
        }

        @Override
        public boolean hasNext() {
            return index < sessionContextsList.size();
        }

        @Override
        public Object next() {
            if (index < sessionContextsList.size()) {
                sessionContexts.set((SessionContext) sessionContextsList.get(index));
                index++;
            }
            return null;
        }

        @Override
        public void remove() {
        }
    }

    private Set<SessionContext> seenSessionContexts = Collections.newSetFromMap(new WeakHashMap<SessionContext, Boolean>());
    public ThreadLocal<SessionContext> sessionContexts;

    @Override
    public Iterator<Object> iterator() {
        return new SessionContextsIterator(new ArrayList<>(seenSessionContexts));
    }

    public void addSessionContext() {
        if (sessionContexts.get() != null) {
            seenSessionContexts.add(sessionContexts.get());
        }
    }

    public void removeSessionContext() {
        if (sessionContexts.get() != null) {
            seenSessionContexts.remove(sessionContexts.get());
        }
    }

}
