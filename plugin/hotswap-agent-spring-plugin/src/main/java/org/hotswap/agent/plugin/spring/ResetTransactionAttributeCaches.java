package org.hotswap.agent.plugin.spring;
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
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.AbstractFallbackTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class ResetTransactionAttributeCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetTransactionAttributeCaches.class);


    private static Map<Object, TransactionAttribute> attributeCache;

    public static void reset(DefaultListableBeanFactory beanFactory) {
        if (attributeCache == null) {
            final AbstractFallbackTransactionAttributeSource transactionAttributeSource = beanFactory.getBean("transactionAttributeSource", AbstractFallbackTransactionAttributeSource.class);
            try {
                Field attributeCacheField = AbstractFallbackTransactionAttributeSource.class.getDeclaredField("attributeCache");
                attributeCacheField.setAccessible(true);
                attributeCache = (Map<Object, TransactionAttribute>) attributeCacheField.get(transactionAttributeSource);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        attributeCache.clear();
    }
}
