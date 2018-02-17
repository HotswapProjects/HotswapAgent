package org.hotswap.agent.plugin.myfaces;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.faces.view.ViewScoped;

import org.apache.myfaces.cdi.view.ViewScopeBeanHolder;
import org.apache.myfaces.cdi.view.ViewScopeContextImpl;
import org.apache.myfaces.cdi.view.ViewScopeContextualStorage;
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
        private List<String> storageIdList;
        private Map<String, ViewScopeContextualStorage> storageMap;

        public ViewContextsIterator(ViewScopeBeanHolder viewScopeBeanHolder) {
            this.storageMap = viewScopeBeanHolder.getStorageMap();
            this.storageIdList = new ArrayList<>(viewScopeBeanHolder.getStorageMap().keySet());
        }

        @Override
        public boolean hasNext() {
            return index < storageIdList.size();
        }

        @Override
        public Object next() {
            if (index < storageIdList.size()) {
                String viewId = storageIdList.get(index);
                setCurrentViewStorage(viewId, storageMap.get(viewId));
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
            setCurrentViewStorage(null, null);
            setViewScopeForceActive(false);
        }

        private void setCurrentViewStorage(String viewId, ViewScopeContextualStorage viewScopeContextualStorage) {
            ReflectionHelper.set(null, ViewScopeContextImpl.class, MyFacesTransformer.HA_CURRENT_VIEW_SCOPE_ID, viewId);
            ReflectionHelper.set(null, ViewScopeContextImpl.class, MyFacesTransformer.HA_CURRENT_VIEW_SCOPE_STORAGE, viewScopeContextualStorage);
        }

        private void setViewScopeForceActive(boolean forceActive) {
            ReflectionHelper.set(null, ViewScopeContextImpl.class, MyFacesTransformer.HA_FORCE_IS_ACTIVE, forceActive);
        }

    }

    @Override
    public Iterator<Object> iterator() {
        BeanManager beanManager = CDI.current().getBeanManager();

        Bean<ViewScopeBeanHolder> bean = resolveBean(beanManager, ViewScopeBeanHolder.class);
        if (bean != null) {
            Context sessionContext = beanManager.getContext(bean.getScope());
            if (sessionContext != null) {
                ViewScopeBeanHolder storageInSession = sessionContext.get(bean);
                return new ViewContextsIterator(storageInSession);
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
