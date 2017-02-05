package org.hotswap.agent.plugin.owb.command;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.context.ConversationContext;
import org.apache.webbeans.context.SessionContext;
import org.apache.webbeans.web.context.ServletRequestContext;
import org.apache.webbeans.web.context.WebContextsService;
import org.hotswap.agent.logging.AgentLogger;

/**
 * The Class HaWebContextsService. Keep web contexts to make them available for HotswapAgent
 *
 * @author Vladimir Dvorak
 */
public class HaWebContextsService extends WebContextsService {

    private static AgentLogger LOGGER = AgentLogger.getLogger(HaWebContextsService.class);

    private boolean fromConversation = false;

    private Map<ServletRequestContext, ConversationContext> requestContext2ConversationMap = new WeakHashMap<>();
    private Map<ServletRequestContext, SessionContext> requestContext2SessionMap = new WeakHashMap<>();
    private Map<ConversationContext, SessionContext> conversation2SessionMap = new WeakHashMap<>();
    private Map<SessionContext, Boolean> sessionMap = new WeakHashMap<>();

    public class WebContextsCombination {

        public final ServletRequestContext requestContext;
        public final ConversationContext conversationContext;
        public final SessionContext sessionContext;

        public WebContextsCombination(ServletRequestContext requestContext, ConversationContext conversationContext, SessionContext sessionContext) {
            this.requestContext = requestContext;
            this.conversationContext = conversationContext;
            this.sessionContext = sessionContext;
        }
    }

    private class ThreadLocalSessionContextSubscriber extends ThreadLocal<SessionContext> {
        @Override
        public void set(SessionContext  value) {
            if (value == null) {
                if (super.get() != null) {
                    sessionMap.remove(super.get());
                }
            } else {
                if (!fromConversation) {
                    registerTriplet(requestContexts.get(), conversationContexts.get(), value);
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
            if (value == null && super.get() != null) {
                conversation2SessionMap.remove(super.get());
            } else {
                if (fromConversation) {
                    registerTriplet(requestContexts.get(), value, sessionContexts.get());
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
            if (value == null && super.get() != null) {
                requestContext2ConversationMap.remove(super.get());
                requestContext2SessionMap.remove(super.get());
            }
            super.set(value);
        }
        public void superSet(ServletRequestContext value) {
            super.set(value);
        }
    }

    public HaWebContextsService(WebBeansContext webBeansContext) {
        super(webBeansContext);
        sessionContexts = new ThreadLocalSessionContextSubscriber();
        conversationContexts = new ThreadLocalConversationContextSubscriber();
        requestContexts = new ThreadLocalRequestContextSubscriber();
        LOGGER.debug("HaWebContextsService created.");
    }

    private void registerTriplet(ServletRequestContext requestContext, ConversationContext conversationContext, SessionContext sessionContext) {
        if (requestContext != null) {
            if (conversationContext != null) {
                requestContext2ConversationMap.put(requestContext, conversationContext);
                conversation2SessionMap.put(conversationContext, sessionContext);
            } else {
                requestContext2SessionMap.put(requestContext, sessionContext);
            }
        } else {
            if (conversationContext != null) {
                conversation2SessionMap.put(conversationContext, sessionContext);
            }
        }
        sessionMap.put(sessionContext, true);
    }

    public List<WebContextsCombination> getWebContextsCombinationList() {
        List<WebContextsCombination> result = new ArrayList<>();
        Map<ConversationContext, Boolean> foundConversationContexts = new IdentityHashMap<>();
        Map<SessionContext, Boolean> foundSessionContexts = new IdentityHashMap<>();

        for (Map.Entry<ServletRequestContext, ConversationContext> entry: requestContext2ConversationMap.entrySet()) {
            foundConversationContexts.put(entry.getValue(), true);
            SessionContext sessionContext = conversation2SessionMap.get(entry.getValue());
            if (sessionContext != null) {
                foundSessionContexts.put(sessionContext, true);
                WebContextsCombination wcc = new WebContextsCombination(entry.getKey(), entry.getValue(), sessionContext);
                result.add(wcc);
            } else {
                LOGGER.error("Session context is null");
            }
        }

        for (Map.Entry<ConversationContext, SessionContext> entry: conversation2SessionMap.entrySet()) {
            if (!foundConversationContexts.containsKey(entry.getKey())) {
                foundSessionContexts.put(entry.getValue(), true);
                WebContextsCombination wcc = new WebContextsCombination(null, entry.getKey(), entry.getValue());
                result.add(wcc);
            }
        }

        for (Map.Entry<ServletRequestContext, SessionContext> entry: requestContext2SessionMap.entrySet()) {
            if (!foundSessionContexts.containsKey(entry.getValue())) {
                foundSessionContexts.put(entry.getValue(), true);
                WebContextsCombination wcc = new WebContextsCombination(entry.getKey(), null, entry.getValue());
                result.add(wcc);
            }
        }

        for (Map.Entry<SessionContext, Boolean> entry: sessionMap.entrySet()) {
            if (!foundSessionContexts.containsKey(entry.getKey())) {
                WebContextsCombination wcc = new WebContextsCombination(null, null, entry.getKey());
                result.add(wcc);
            }
        }
        return result;
    }

    public void setWebContextsCombination(WebContextsCombination wcc) {
        ((ThreadLocalRequestContextSubscriber)requestContexts).superSet(wcc.requestContext);
        ((ThreadLocalConversationContextSubscriber)conversationContexts).superSet(wcc.conversationContext);
        ((ThreadLocalSessionContextSubscriber)sessionContexts).superSet(wcc.sessionContext);
    }

    public ConversationContext getConversationContext(boolean create, boolean ignoreProblems) {
        try {
            fromConversation = true;
            return super.getConversationContext(create, ignoreProblems);
        } finally {
            fromConversation = false;
        }
    }
}
