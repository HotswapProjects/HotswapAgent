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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.plugin.spring.transformers.api.BeanFactoryLifecycle;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BeanFactoryAssistant {
    private ConfigurableListableBeanFactory beanFactory;
    private AtomicInteger reloadTimes;
    private long lastReloadTime;
    private volatile boolean isReload = false;

    // keep the bean name and xml file relation for the beans which are defined in xml file and the bean contains placeholder
    Map<String, String> placeHolderXmlMapping = new ConcurrentHashMap<>();

    private static Map<ConfigurableListableBeanFactory, BeanFactoryAssistant> beanFactoryAssistants = new ConcurrentHashMap<>(4);

    public BeanFactoryAssistant(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.reloadTimes = new AtomicInteger(0);
        this.lastReloadTime = System.currentTimeMillis();
        beanFactoryAssistants.put(beanFactory, this);
    }

    public static BeanFactoryAssistant getBeanFactoryAssistant(ConfigurableListableBeanFactory beanFactory) {
        return beanFactoryAssistants.get(beanFactory);
    }

    public void increaseReloadTimes() {
        this.reloadTimes.incrementAndGet();
        this.lastReloadTime = System.currentTimeMillis();
    }

    public ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }

    public void reset() {
        this.reloadTimes.set(0);
        this.lastReloadTime = System.currentTimeMillis();
    }

    public int getReloadTimes() {
        return reloadTimes.get();
    }

    public long getLastReloadTime() {
        return lastReloadTime;
    }

    public boolean isReload() {
        return isReload;
    }

    public void setReload(boolean isReload) {
        this.isReload = isReload;
    }

    /**
     * check the bean is destroyed or not
     * @param beanName
     * @return
     */
    public boolean isDestroyedBean(String beanName) {
        if (beanFactory instanceof BeanFactoryLifecycle) {
            return ((BeanFactoryLifecycle) beanFactory).hotswapAgent$isDestroyedBean(beanName);
        }
        return false;
    }
}
