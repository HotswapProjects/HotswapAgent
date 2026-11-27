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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.springframework.core.InfrastructureProxy;

/**
 * MethodInterceptor for java.lang.reflect bean Proxies. If the bean inside the proxy is cleared, it will be retrieved
 * from the factory on demand.
 *
 * @author Erki Ehtla
 *
 */
public class HotswapSpringInvocationHandler extends DetachableBeanHolder implements InvocationHandler {

    private static final long serialVersionUID = 8037007940960065166L;

    // whether the bean is an InfrastructureProxy itself, then it is asked for the wrapped object
    private final boolean infrastructureProxy;

    /**
     * @param beanFactry   Spring beanFactory
     * @param bean         Spring bean
     * @param paramClasses Parameter Classes of the Spring beanFactory method which returned the bean. The method is named
     *                     ProxyReplacer.FACTORY_METHOD_NAME
     * @param paramValues  Parameter values of the Spring beanFactory method which returned the bean. The method is named
     *                     ProxyReplacer.FACTORY_METHOD_NAME
     */
    public HotswapSpringInvocationHandler(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
        super(bean, beanFactry, paramClasses, paramValues);
        this.infrastructureProxy = bean instanceof InfrastructureProxy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == InfrastructureProxy.class && !infrastructureProxy) {
            // The key of the resources of a transaction, e.g. of the connection bound to a data source. Not the
            // bean: a reload gives the proxy another one, and a running transaction must still find its resources.
            return this;
        }
        if (method.getDeclaringClass() == Object.class && method.getName().equals("equals")) {
            // the bean does not know the proxies of the agent, it is compared with the bean of the other proxy
            return proxy == args[0] || (Boolean) doInvoke(method, new Object[] {unwrap(args[0])});
        }
        return doInvoke(method, args);
    }

    private static Object unwrap(Object other) throws Throwable {
        if (other != null && Proxy.isProxyClass(other.getClass())
                && Proxy.getInvocationHandler(other) instanceof HotswapSpringInvocationHandler) {
            return ((HotswapSpringInvocationHandler) Proxy.getInvocationHandler(other)).getBean();
        }
        return other;
    }

    private Object doInvoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(getBean(), args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}