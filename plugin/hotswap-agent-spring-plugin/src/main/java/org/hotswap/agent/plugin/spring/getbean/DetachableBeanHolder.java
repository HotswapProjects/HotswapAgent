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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.CollectionUtils;
import org.hotswap.agent.util.spring.util.StringUtils;

/**
 * Loadable detachable Spring bean holder
 *
 * @author Erki Ehtla
 */
public class DetachableBeanHolder implements Serializable {

    private static final long serialVersionUID = -7443802320153815102L;

    private String beanName;
    private Object bean;
    private Object beanFactory;
    /**
     * Q:Why beanProxies is a map?
     * A:beanProxies used to be a list structure, without beanName, so it could not be cleared
     * based on beanName, and could only be cleared in full, which caused those beans
     * that had not changed to be cleared, which was unnecessary.
     * -
     * Q:Why value is a list?
     * A:If want to let HotSwap to take effect on multiple prototype beans of the same
     * class, a structure with one name corresponding to multiple beans is required.
     * SpringPluginTest#hotswapPrototypeTestExistingInstance can test prototype beans.
     */
    private static final Map<String, List<WeakReference<DetachableBeanHolder>>> beanProxies = new ConcurrentHashMap<>();
    private static AgentLogger LOGGER = AgentLogger.getLogger(DetachableBeanHolder.class);

    /**
     * @param bean       Spring Bean this object holds
     * @param beanFactory Spring factory that produced the bean with a ProxyReplacer.FACTORY_METHOD_NAME method
     */
    public DetachableBeanHolder(Object bean, Object beanFactory, Object beanName) {
        String beanNameStr = beanName.toString();
        this.bean = bean;
        this.beanFactory = beanFactory;
        this.beanName = beanNameStr;

        if (beanName != null) {
            synchronized (beanProxies) {
                beanProxies.computeIfAbsent(beanNameStr, k -> new ArrayList<>());
                beanProxies.get(beanName).add(new WeakReference<>(this));
            }
        }
    }

    /**
     * Clears the bean references inside the beanName's proxy
     */
    public static void detachBean(String beanName) {
        if (StringUtils.isEmpty(beanName)) {
            return;
        }
        synchronized (beanProxies) {
            if (beanProxies.containsKey(beanName)) {
                List<WeakReference<DetachableBeanHolder>> weakReferences = beanProxies.get(beanName);
                if (CollectionUtils.isEmpty(weakReferences)) {
                    return;
                }
                for (WeakReference<DetachableBeanHolder> weakReference : weakReferences) {
                    DetachableBeanHolder beanHolder = weakReference.get();
                    if (beanHolder != null) {
                        beanHolder.detach();
                    }
                    LOGGER.info("{} Spring proxy reset", beanName);
                }
            }
        }
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
                Class<?>[] parameterTypes = factoryMethod.getParameterTypes();
                if (ProxyReplacer.FACTORY_METHOD_NAME.equals(factoryMethod.getName())
                        && parameterTypes.length == 1 && parameterTypes[0] == String.class) {

                    Object freshBean = factoryMethod.invoke(beanFactory, beanName);

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
                    if (beanCopy == null) {
                        LOGGER.debug("Bean '{}' loaded", beanName);
                        break;
                    }
                    LOGGER.info("Bean '{}' loaded", beanName);
                    break;
                }
            }
        }
        return beanCopy;
    }

}