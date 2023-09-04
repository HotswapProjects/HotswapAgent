package org.hotswap.agent.plugin.spring.core;
/*
 * Copyright 2013-2023 the HotswapAgent authors.
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

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.interceptor.AbstractFallbackTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Field;
import java.util.Map;

public class ResetTransactionAttributeCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetTransactionAttributeCaches.class);

    private static Map<Object, TransactionAttribute> attributeCache;
    private static boolean tried = false;

    public static void reset(DefaultListableBeanFactory beanFactory) {
        if (!beanFactory.containsBean("transactionAttributeSource")) {
            return;
        }
        try {
            if (attributeCache == null && !tried) {
                //only try once
                tried = true;
                final AbstractFallbackTransactionAttributeSource transactionAttributeSource = beanFactory.getBean("transactionAttributeSource", AbstractFallbackTransactionAttributeSource.class);
                Field attributeCacheField = AbstractFallbackTransactionAttributeSource.class.getDeclaredField("attributeCache");
                attributeCacheField.setAccessible(true);
                attributeCache = (Map<Object, TransactionAttribute>) attributeCacheField.get(transactionAttributeSource);
            }
            if (attributeCache != null) {
                attributeCache.clear();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reset @Transactional cache", e);
        }
    }
}
