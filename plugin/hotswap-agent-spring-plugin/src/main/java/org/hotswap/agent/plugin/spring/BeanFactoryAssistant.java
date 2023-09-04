package org.hotswap.agent.plugin.spring;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BeanFactoryAssistant {
    private ConfigurableListableBeanFactory beanFactory;
    private AtomicInteger reloadTimes;
    private long lastReloadTime;

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
}
