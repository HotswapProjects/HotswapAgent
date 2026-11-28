/*
 * Copyright 2013-2026 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.files;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Clears the caches of the message sources of a bean factory, so that they read the changed message bundles
 * (e.g. messages_en.properties) again.
 */
public class MessageSourceReload {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(MessageSourceReload.class);

    /**
     * Clear the caches of the message sources created in the bean factory and of their parent message sources.
     *
     * @param beanFactory the bean factory
     */
    public static void clearCaches(DefaultListableBeanFactory beanFactory) {
        Set<MessageSource> cleared = Collections.newSetFromMap(new IdentityHashMap<>());
        // do not create message sources that do not exist yet, they read the bundles when they are created
        for (String beanName : beanFactory.getBeanNamesForType(MessageSource.class, true, false)) {
            if (!beanFactory.containsSingleton(beanName)) {
                continue;
            }
            Object bean = beanFactory.getSingleton(beanName);
            MessageSource messageSource = bean instanceof MessageSource ? (MessageSource) bean : null;
            while (messageSource != null && cleared.add(messageSource)) {
                clearCache(beanFactory, messageSource);
                messageSource = messageSource instanceof HierarchicalMessageSource ?
                        ((HierarchicalMessageSource) messageSource).getParentMessageSource() : null;
            }
        }
    }

    private static void clearCache(DefaultListableBeanFactory beanFactory, MessageSource messageSource) {
        try {
            if (messageSource instanceof ReloadableResourceBundleMessageSource) {
                ((ReloadableResourceBundleMessageSource) messageSource).clearCache();
            } else if (messageSource instanceof ResourceBundleMessageSource) {
                clearResourceBundleCache((ResourceBundleMessageSource) messageSource);
            } else {
                return;
            }
            LOGGER.debug("Cleared the cache of the message source {} of {}", ObjectUtils.identityToString(messageSource),
                    ObjectUtils.identityToString(beanFactory));
        } catch (Exception e) {
            LOGGER.warning("Unable to clear the cache of the message source {}", e,
                    ObjectUtils.identityToString(messageSource));
        }
    }

    private static void clearResourceBundleCache(ResourceBundleMessageSource messageSource) {
        // Spring has no method to clear the cache of a ResourceBundleMessageSource, the fields are the same since 3.x
        clearMap(ReflectionHelper.get(messageSource, ResourceBundleMessageSource.class, "cachedResourceBundles"));
        clearMap(ReflectionHelper.get(messageSource, ResourceBundleMessageSource.class, "cachedBundleMessageFormats"));
        // the JDK keeps loaded bundles too, per class loader
        ClassLoader bundleClassLoader = (ClassLoader) ReflectionHelper.invoke(messageSource,
                ResourceBundleMessageSource.class, "getBundleClassLoader", new Class<?>[0]);
        if (bundleClassLoader != null) {
            ResourceBundle.clearCache(bundleClassLoader);
        }
    }

    private static void clearMap(Object map) {
        if (map instanceof Map) {
            // Spring before 5 guards the plain maps by synchronizing on them
            synchronized (map) {
                ((Map<?, ?>) map).clear();
            }
        }
    }
}
