package org.hotswap.agent.plugin.deltaspike.context;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.apache.deltaspike.core.api.scope.WindowScoped;
import org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder;
import org.apache.deltaspike.core.util.context.ContextualStorage;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Iterate over all WindowContexts in current session context
 *
 * @author Vladimir Dvorak
 */
public class WindowContextsTracker implements Iterable, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CUSTOM_CONTEXT_TRACKER_FIELD = "$$ha$customContextTrackers";

    private static AgentLogger LOGGER = AgentLogger.getLogger(WindowContextsTracker.class);

    public static class WindowContextsIterator implements Iterator<Object>, Closeable {

        private int index = 0;
        private List<String> windowIdList;

        private boolean oldWindowContextQuotaHandler;

        public WindowContextsIterator(List<String> windowIdList) {
            this.windowIdList = windowIdList;
        }

        @Override
        public boolean hasNext() {
            return index < windowIdList.size();
        }

        @Override
        public Object next() {
            if (index < windowIdList.size()) {
                HaWindowIdHolder.haWindowId = windowIdList.get(index);
                if (index == 0) {
                    oldWindowContextQuotaHandler = getWindowContextQuotaHandler();
                    // Force skip window context quota handling since it use request scope, thats is
                    // not available in hotswap
                    setWindowContextQuotaHandler(false);
                }
                index++;
            }
            return null;
        }

        @Override
        public void remove() {
        }

        @Override
        public void close() throws IOException {
            HaWindowIdHolder.haWindowId = null;
            if (index > 0) {
                setWindowContextQuotaHandler(oldWindowContextQuotaHandler);
            }
        }

        private boolean getWindowContextQuotaHandler() {
            return (boolean) ReflectionHelper.get(getWindowBeanHolder(), WindowBeanHolder.class, "windowContextQuotaHandlerEnabled");
        }

        private void setWindowContextQuotaHandler(boolean value) {
            ReflectionHelper.set(getWindowBeanHolder(), WindowBeanHolder.class, "windowContextQuotaHandlerEnabled", value);
        }

        private WindowBeanHolder getWindowBeanHolder() {
            BeanManager beanManager = CDI.current().getBeanManager();
            Bean<WindowBeanHolder> bean = resolveBean(beanManager, WindowBeanHolder.class);
            return beanManager.getContext(bean.getScope()).get(bean);
        }

    }

    @Override
    public Iterator<Object> iterator() {
        BeanManager beanManager = CDI.current().getBeanManager();

        Bean<WindowBeanHolder> bean = resolveBean(beanManager, WindowBeanHolder.class);
        if (bean != null) {
            Context sessionContext = beanManager.getContext(bean.getScope());
            if (sessionContext != null) {
                WindowBeanHolder beanHolder = sessionContext.get(bean);
                Map<String, ContextualStorage> storageMap = (Map<String, ContextualStorage>) ReflectionHelper.get(beanHolder, "storageMap");
                if (storageMap != null) {
                    return new WindowContextsIterator(new ArrayList<String>(storageMap.keySet()));
                }
            } else {
                LOGGER.debug("No WindowBeanHolder found, no active session context.");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanClass) {
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        for (Bean<?> bean : beans) {
            if (bean.getBeanClass() == beanClass) {
                return (Bean<T>) beanManager.resolve(Collections.<Bean<?>>singleton(bean));
            }
        }
        return (Bean<T>) beanManager.resolve(beans);
    }

    /**
     * Register to current session's tracker field
     */
    public static void register() {
        BeanManager beanManager = CDI.current().getBeanManager();
        Context context = null;

        try {
            Method m = beanManager.getClass().getMethod("getUnwrappedContext", Class.class);
            context = (Context) m.invoke(beanManager, SessionScoped.class);
        } catch (NoSuchMethodException e) {
            context = beanManager.getContext(SessionScoped.class);
        } catch (Exception e) {
            assert(false);
        }

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
            Map m = (Map) ReflectionHelper.get(context, CUSTOM_CONTEXT_TRACKER_FIELD);
            if (!m.containsKey(WindowScoped.class.getName())) {
                m.put(WindowScoped.class.getName(), new WindowContextsTracker());
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("Field '{}' not found in context class '{}'.", CUSTOM_CONTEXT_TRACKER_FIELD, context.getClass().getName());
        }
    }
}
