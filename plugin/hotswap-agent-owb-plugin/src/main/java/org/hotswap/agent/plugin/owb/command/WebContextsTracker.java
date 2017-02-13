package org.hotswap.agent.plugin.owb.command;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.webbeans.context.ConversationContext;
import org.apache.webbeans.context.SessionContext;
import org.apache.webbeans.web.context.ServletRequestContext;

/**
 * The Class WebContextsTracker.
 *
 * @author Vladimir Dvorak
 */
public class WebContextsTracker implements Iterable {

    public static class WebContextsSet {

        public final ServletRequestContext requestContext;
        public final ConversationContext conversationContext;
        public final SessionContext sessionContext;

        public WebContextsSet(ServletRequestContext requestContext, ConversationContext conversationContext, SessionContext sessionContext) {
            this.requestContext = requestContext;
            this.conversationContext = conversationContext;
            this.sessionContext = sessionContext;
        }
    }

    public class WebContextsSetIterator implements Iterator {

        private int index = 0;
        private List<WebContextsSet> wcsList;

        public WebContextsSetIterator(List wcsList) {
            this.wcsList = wcsList;
        }

        @Override
        public boolean hasNext() {
            return index < wcsList.size();
        }

        @Override
        public Object next() {
            if (index < wcsList.size()) {
                setWebContextsSet(wcsList.get(index));
                index++;
            }
            return null;
        }

        @Override
        public void remove() {
        }
    }

    private Map<ServletRequestContext, SessionContext> request2SessionMap = new WeakHashMap<>();
    private Map<ServletRequestContext, ConversationContext> request2ConversationMap = new WeakHashMap<>();
    private Map<ConversationContext, SessionContext> conversation2SessionMap = new WeakHashMap<>();
    private Map<ServletRequestContext, Boolean> requestMap = new WeakHashMap<>();
    private Map<SessionContext, Boolean> sessionMap = new WeakHashMap<>();

    public ThreadLocalSessionContextSubscriber sessionContexts = new ThreadLocalSessionContextSubscriber();
    public ThreadLocalConversationContextSubscriber conversationContexts = new ThreadLocalConversationContextSubscriber();
    public ThreadLocalRequestContextSubscriber requestContexts = new ThreadLocalRequestContextSubscriber();

    private class ThreadLocalSessionContextSubscriber extends ThreadLocal<SessionContext> {
        @Override
        public void set(SessionContext  value) {
            if (value == null) {
                if (super.get() != null) {
                    sessionMap.remove(super.get());
                }
            } else {
                sessionMap.put(value, true);
                if (requestContexts.get() != null) {
                    request2SessionMap.put(requestContexts.get(), value);
                }
                if (conversationContexts.get() != null) {
                    conversation2SessionMap.put(conversationContexts.get(), value);
                }
            }
            super.set(value);
        }
        public void superSet(SessionContext value) {
            super.set(value);
        }
    }

    private class ThreadLocalConversationContextSubscriber extends ThreadLocal<ConversationContext> {
        @Override
        public void set(ConversationContext value) {
            if (value == null) {
                if (super.get() != null) {
                    conversation2SessionMap.remove(super.get());
                }
            } else {
                if (sessionContexts.get() != null) {
                    conversation2SessionMap.put(value, sessionContexts.get());
                }
                if (requestContexts.get() != null) {
                    request2ConversationMap.put(requestContexts.get(), value);
                }
            }
            super.set(value);
        }
        public void superSet(ConversationContext value) {
            super.set(value);
        }
    }

    private class ThreadLocalRequestContextSubscriber extends ThreadLocal<ServletRequestContext> {
        @Override
        public void set(ServletRequestContext value) {
            if (value == null) {
                if (super.get() != null) {
                    requestMap.remove(super.get());
                    request2SessionMap.remove(super.get());
                }
            } else {
                requestMap.put(value, true);
                if (sessionContexts.get() != null) {
                    request2SessionMap.put(value, sessionContexts.get());
                }
            }
            super.set(value);
        }
        public void superSet(ServletRequestContext value) {
            super.set(value);
        }
    }

    @Override
    public Iterator iterator() {

        List<WebContextsSet> wcsList = new ArrayList<>();

        Map<ConversationContext, Boolean> foundConversationContexts = new IdentityHashMap<>();
        Map<SessionContext, Boolean> foundSessionContexts = new IdentityHashMap<>();

        // 1. iterate over request->session map and join conversation
        for (Map.Entry<ServletRequestContext, SessionContext> entry: request2SessionMap.entrySet()) {
            WebContextsSet wcc = createWebContextSet(entry.getKey(), request2ConversationMap.get(entry.getKey()), entry.getValue());
            if (wcc != null) {
                wcsList.add(wcc);
                if (wcc.conversationContext != null) {
                    foundConversationContexts.put(wcc.conversationContext, true);
                }
                if (wcc.sessionContext != null) {
                    foundSessionContexts.put(wcc.sessionContext, true);
                }
            }
        }

        // 2. iterate over conversation->session map and use only not found conversations in step (1)
        for (Map.Entry<ConversationContext, SessionContext> entry: conversation2SessionMap.entrySet()) {
            if (!foundConversationContexts.containsKey(entry.getKey())) {
                WebContextsSet wcc = createWebContextSet(null, entry.getKey(), entry.getValue());
                if (wcc != null) {
                    wcsList.add(wcc);
                    if (wcc.sessionContext != null) {
                        foundSessionContexts.put(wcc.sessionContext, true);
                    }
                }
            }
        }

        // 3. iterate over session map and use only not found sessions in step (1-2)
        for (Map.Entry<SessionContext, Boolean> entry: sessionMap.entrySet()) {
            if (!foundSessionContexts.containsKey(entry.getKey())) {
                WebContextsSet wcc = createWebContextSet(null, null, entry.getKey());
                if (wcc != null) {
                    wcsList.add(wcc);
                }
            }
        }

        // 4. for completeness use all standalone requests
        for (Map.Entry<ServletRequestContext, Boolean> entry: requestMap.entrySet()) {
            if (!request2SessionMap.containsKey(entry.getKey())) {
                WebContextsSet wcc = createWebContextSet(entry.getKey(), null, null);
                if (wcc != null) {
                    wcsList.add(wcc);
                }
            }
        }

        return new WebContextsSetIterator(wcsList);
    }

    private WebContextsSet createWebContextSet(ServletRequestContext requestContext,
            ConversationContext conversationContext, SessionContext sessionContext) {
        if (requestContext == null && conversationContext == null && sessionContext == null) {
            return null;
        }
        if (requestContext != null && !requestContext.isActive() ||
            conversationContext != null && !conversationContext.isActive() ||
            sessionContext != null && !sessionContext.isActive()) {
            return null;
        }
        return new WebContextsSet(requestContext, conversationContext, sessionContext);
    }

    public void setWebContextsSet(WebContextsSet wcc) {
        ((ThreadLocalRequestContextSubscriber)requestContexts).superSet(wcc.requestContext);
        ((ThreadLocalConversationContextSubscriber)conversationContexts).superSet(wcc.conversationContext);
        ((ThreadLocalSessionContextSubscriber)sessionContexts).superSet(wcc.sessionContext);
    }

}
