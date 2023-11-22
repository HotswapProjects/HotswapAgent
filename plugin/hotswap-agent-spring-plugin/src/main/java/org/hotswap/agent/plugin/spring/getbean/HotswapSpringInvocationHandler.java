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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * MethodInterceptor for java.lang.reflect bean Proxies. If the bean inside the proxy is cleared, it will be retrieved
 * from the factory on demand.
 *
 * @author Erki Ehtla
 *
 */
public class HotswapSpringInvocationHandler extends DetachableBeanHolder implements InvocationHandler {

    private static final long serialVersionUID = 8037007940960065166L;

    /**
     *
     * @param beanFactry
     *            Spring beanFactory
     * @param bean
     *            Spring bean
     * @param paramClasses
     *            Parameter Classes of the Spring beanFactory method which returned the bean. The method is named
     *            ProxyReplacer.FACTORY_METHOD_NAME
     * @param paramValues
     *            Parameter values of the Spring beanFactory method which returned the bean. The method is named
     *            ProxyReplacer.FACTORY_METHOD_NAME
     */
    public HotswapSpringInvocationHandler(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
        super(bean, beanFactry, paramClasses, paramValues);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("getWrappedObject")
                && method.getDeclaringClass().getName().equals("org.springframework.core.InfrastructureProxy")) {
            for (Class<?> beanInterface : getBean().getClass().getInterfaces()) {
                if (beanInterface.getName().equals("org.springframework.core.InfrastructureProxy")) {
                    return doInvoke(method, args);
                }
            }
            return getBean();
        }
        return doInvoke(method, args);
    }

    private Object doInvoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(getBean(), args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}