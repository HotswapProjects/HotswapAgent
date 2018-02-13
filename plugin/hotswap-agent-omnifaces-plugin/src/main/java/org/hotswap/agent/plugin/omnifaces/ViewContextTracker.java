package org.hotswap.agent.plugin.omnifaces;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.omnifaces.cdi.BeanStorage;
import org.omnifaces.cdi.ViewScoped;
import org.omnifaces.cdi.viewscope.ViewScopeContext;
import org.omnifaces.cdi.viewscope.ViewScopeStorageInSession;

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
        private ViewScopeStorageInSession storageInSession;
        private List<UUID> storageIdList;

        public ViewContextsIterator(ViewScopeStorageInSession storageInSession, ArrayList<UUID> storageIdList) {
            this.storageInSession = storageInSession;
            this.storageIdList = storageIdList;
        }

        @Override
        public boolean hasNext() {
            return index < storageIdList.size();
        }

        @Override
        public Object next() {
            if (index < storageIdList.size()) {
                setHaViewStorageId(storageIdList.get(index));
                setViewScopeForceActive(true);
                index++;
            }
            return null;
        }

        @Override
        public void remove() {
        }

        @Override
        public void close() throws IOException {
            setHaViewStorageId(null);
            setViewScopeForceActive(false);
        }

        private void setHaViewStorageId(UUID viewStorageId) {
            ReflectionHelper.set(storageInSession, ViewScopeStorageInSession.class, OmnifacesTransformer.HA_BEAN_STORAGE_ID, viewStorageId);
        }

        private void setViewScopeForceActive(boolean forceActive) {
            ReflectionHelper.set(null, ViewScopeContext.class, OmnifacesTransformer.HA_FORCE_IS_ACTIVE, forceActive);
        }

    }

    @Override
    public Iterator<Object> iterator() {
        BeanManager beanManager = CDI.current().getBeanManager();

        Bean<ViewScopeStorageInSession> bean = resolveBean(beanManager, ViewScopeStorageInSession.class);
        if (bean != null) {
            Context sessionContext = beanManager.getContext(bean.getScope());
            if (sessionContext != null) {
                ViewScopeStorageInSession storageInSession = sessionContext.get(bean);
                Map<UUID, BeanStorage> activeScopesMap = (Map<UUID, BeanStorage>) ReflectionHelper.get(storageInSession, "activeViewScopes");
                return new ViewContextsIterator(storageInSession, new ArrayList<UUID>(activeScopesMap.keySet()));
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
            context = beanManager.getContext(SessionScoped.class);
            Context delegate = (Context) ReflectionHelper.invoke(context, context.getClass(), "$$ha$delegate", null);
            if (delegate != null && delegate != context) {
                context = delegate;
            }
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            LOGGER.error("Delegate failed.", e);
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
            Map m = (Map) ReflectionHelper.get(context, OmnifacesTransformer.CUSTOM_CONTEXT_TRACKER_FIELD);
            if (!m.containsKey(ViewScoped.class.getName())) {
                m.put(ViewScoped.class.getName(), new ViewContextTracker());
                LOGGER.debug("ViewContextTracker added to context '{}'", context);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("Field '{}' not found in context class '{}'.", OmnifacesTransformer.CUSTOM_CONTEXT_TRACKER_FIELD,
                    context.getClass().getName());
        }
    }
}
