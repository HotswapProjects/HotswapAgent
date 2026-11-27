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
package org.hotswap.agent.plugin.spring.getbean;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hotswap.agent.logging.AgentLogger;

/**
 * Loadable detachable Spring bean holder
 *
 * @author Erki Ehtla
 */
public class DetachableBeanHolder implements Serializable {

    private static final long serialVersionUID = -7443802320153815102L;

    private Object bean;
    private Object beanFactory;
    private Class<?>[] paramClasses;
    private Object[] paramValues;
    private static List<WeakReference<DetachableBeanHolder>> beanProxies =
            Collections.synchronizedList(new ArrayList<>());
    private static AgentLogger LOGGER = AgentLogger.getLogger(DetachableBeanHolder.class);

    /**
     *
     * @param bean
     *            Spring Bean this object holds
     * @param beanFactry
     *            Spring factory that produced the bean with a ProxyReplacer.FACTORY_METHOD_NAME method
     * @param paramClasses
     * @param paramValues
     */
    public DetachableBeanHolder(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
        if (bean == null) {
            LOGGER.error("Bean is null. The param value: {}", Arrays.toString(paramValues));
        }
        this.bean = bean;
        this.beanFactory = beanFactry;
        this.paramClasses = paramClasses;
        this.paramValues = paramValues;
        beanProxies.add(new WeakReference<>(this));
    }

    /**
     * Clears the bean references inside all of the proxies
     */
    public static void detachBeans() {
        int i = 0;
        synchronized (beanProxies) {
            while (i < beanProxies.size()) {
                DetachableBeanHolder beanHolder = beanProxies.get(i).get();
                if (beanHolder != null) {
                    beanHolder.detach();
                    i++;
                } else {
                    beanProxies.remove(i);
                }
            }
        }
        if (i > 0) {
            LOGGER.debug("{} Spring proxies reset", i);
        } else {
            LOGGER.debug("No spring proxies reset");
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
                    } else if (freshBean != null && Proxy.isProxyClass(freshBean.getClass())
                            && Proxy.getInvocationHandler(freshBean) instanceof DetachableBeanHolder) {
                        // the JDK proxy of the agent, maybe this one: the factory has set its bean
                        freshBean = ((DetachableBeanHolder) Proxy.getInvocationHandler(freshBean)).getTarget();
                    }
                    bean = freshBean;
                    beanCopy = bean;
                    if (beanCopy == null) {
                        LOGGER.debug("Bean not loaded, {} ", Arrays.toString(paramValues));
                        break;
                    }
                    LOGGER.debug("Bean '{}' loaded", bean.getClass().getName());
                    break;
                }
            }
        }
        return beanCopy;
    }

    /**
     * Checks if a call of the method intercepted by the proxy goes to the target bean by reflection instead of
     * the CGLIB MethodProxy. The FastClass of the MethodProxy in older CGLIB (Spring before 4.2.x) is generated for
     * the bean class and misses the protected methods inherited from another package, so it fails with
     * "IllegalArgumentException: Protected method", see #316. Public methods keep using the faster MethodProxy.
     *
     * @param method the intercepted method
     * @return true if the method is not public and it can be made accessible
     */
    public static boolean isInvokedByReflection(Method method) {
        if (method == null || Modifier.isPublic(method.getModifiers())) {
            return false;
        }
        try {
            method.setAccessible(true);
            return true;
        } catch (RuntimeException e) {
            // e.g. a protected method of a class from a module that is not open on Java 9+
            LOGGER.trace("Cannot make {} accessible, calling it through the CGLIB MethodProxy", e, method);
            return false;
        }
    }

    /**
     * Calls the method on the target bean by reflection and rethrows the exception thrown by the method itself.
     *
     * @param method the method made accessible by {@link #isInvokedByReflection(Method)}
     * @param target the target bean
     * @param args the method arguments
     * @return the method result
     * @throws Throwable the exception thrown by the method
     */
    public static Object invokeByReflection(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    protected boolean isBeanLoaded(){
        return bean != null;
    }
}
