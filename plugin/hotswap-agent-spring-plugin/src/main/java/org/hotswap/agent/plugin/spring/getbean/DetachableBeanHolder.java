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
package org.hotswap.agent.plugin.spring.getbean;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 *
 * Loadable detachable Spring bean holder
 *
 * @author Erki Ehtla
 *
 */
public class DetachableBeanHolder implements Serializable {

    private static final long serialVersionUID = -7443802320153815102L;

    private Object bean;
    private Object beanFactory;
    private Class<?>[] paramClasses;
    private Object[] paramValues;
    //key: bean name
    private static final Map<String, WeakReference<DetachableBeanHolder>> beanProxies = new ConcurrentHashMap<>();
    //key:bean name, value: proxy bean cached for DefaultListableBeanFactory's getBean(..) methods
    private static final Map<String, Object> HA_PROXIES_CACHE = new ConcurrentHashMap<>();

    private static AgentLogger LOGGER = AgentLogger.getLogger(DetachableBeanHolder.class);

    /**
     * @param bean         Spring Bean this object holds
     * @param beanFactry   Spring factory that produced the bean with a ProxyReplacer.FACTORY_METHOD_NAME method
     * @param paramClasses
     * @param paramValues
     */
    public DetachableBeanHolder(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
        this.bean = bean;
        this.beanFactory = beanFactry;
        this.paramClasses = paramClasses;
        this.paramValues = paramValues;

        String beanName = deduceBeanName(beanFactry, paramClasses, paramValues);
        if (beanName != null) {
            synchronized (beanProxies) {
                if (beanProxies.containsKey(beanName)) {
                    detachBean(beanName);
                }
                beanProxies.put(beanName, new WeakReference<>(this));
            }
        }
    }

    protected static String deduceBeanName(Object beanFactory, Class<?>[] paramClasses, Object[] paramValues) {
        String beanName = null;
        if (String.class.getName().equals(paramClasses[0].getName())) {
            beanName = paramValues[0].toString();
        } else {
            //getBeanNamesForType method will return only one here, because getBean with requiredType param has NoUniqueBeanDefinitionException check
            String[] beanNamesForType = ((DefaultListableBeanFactory) beanFactory).getBeanNamesForType((Class<?>) paramValues[0]);
            if (beanNamesForType.length == 1) {
                beanName = beanNamesForType[0];
            } else {
                //this should never happen
                LOGGER.warning("Method beanNamesForType return unexpected names:{}", String.join(",", beanNamesForType));
            }
        }
        return beanName;
    }

    /**
     * Clears the bean references inside all of the proxies
     */
    public static void detachBeans() {
        int i = 0;
        synchronized (beanProxies) {
            Iterator<Map.Entry<String, WeakReference<DetachableBeanHolder>>> iterator = beanProxies.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, WeakReference<DetachableBeanHolder>> entry = iterator.next();
                DetachableBeanHolder beanHolder = entry.getValue().get();
                if (beanHolder != null) {
                    beanHolder.detach();
                    i++;
                } else {
                    iterator.remove();
                }
            }
            HA_PROXIES_CACHE.clear();
        }
        if (i > 0) {
            LOGGER.info("{} Spring proxies reset", i);
        } else {
            LOGGER.debug("No spring proxies reset");
        }
    }


    /**
     * Clears the bean references inside the beanName's proxy
     */
    public static void detachBean(String beanName) {
        synchronized (beanProxies) {
            if (beanProxies.containsKey(beanName)) {
                DetachableBeanHolder beanHolder = beanProxies.get(beanName).get();
                if (beanHolder != null) {
                    beanHolder.detach();
                } else {
                    beanProxies.remove(beanName);
                }
            }
            HA_PROXIES_CACHE.remove(beanName);
        }
        LOGGER.info("{} Spring proxies reset", beanName);
    }

    /**
     * Clear the bean for this proxy
     */
    public void detach() {
        bean = null;
    }


    /**
     * Sets current target bean.
     * @return current target bean.
     */
    public void setTarget(Object bean) {
        this.bean = bean;
    }

    /**
     * Returns current target bean.
     * @return current target bean.
     */
    public Object getTarget() {
        return bean;
    }

    /**
     * Returns an existing bean instance or retrieves and stores new bean from the Spring BeanFactory
     *
     * @return Bean this instance holds
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public Object getBean() throws IllegalAccessException, InvocationTargetException {
        Object beanCopy = bean;
        if (beanCopy == null) {
            Method[] methods = beanFactory.getClass().getMethods();
            for (Method factoryMethod : methods) {
                if (ProxyReplacer.FACTORY_METHOD_NAME.equals(factoryMethod.getName())
                        && Arrays.equals(factoryMethod.getParameterTypes(), paramClasses)) {

                    Object freshBean = factoryMethod.invoke(beanFactory, paramValues);

                    // Factory returns HA proxy, but current method is invoked from HA proxy!
                    // It might be the same object (if factory returns same object - meaning
                    // that although clearAllProxies() was called, this bean did not change)
                    // Unwrap the target bean, it is always available
                    // see org.hotswap.agent.plugin.spring.getbean.EnhancerProxyCreater.create()
                    if (freshBean instanceof SpringHotswapAgentProxy) {
                        freshBean = ((SpringHotswapAgentProxy) freshBean).$$ha$getTarget();
                    }

                    bean = freshBean;
                    beanCopy = bean;
                    LOGGER.info("Bean '{}' loaded", bean.getClass().getName());
                    break;
                }
            }
        }
        return beanCopy;
    }

    protected static Object getHAProxy(String beanName){
        if (beanName != null){
            return HA_PROXIES_CACHE.get(beanName);
        }
        return null;
    }

    protected static void addHAProxy(String beanName, Object proxy){
        if (beanName != null){
            HA_PROXIES_CACHE.put(beanName, proxy);
        }
    }
    protected boolean isBeanLoaded(){
        return bean != null;
    }
}