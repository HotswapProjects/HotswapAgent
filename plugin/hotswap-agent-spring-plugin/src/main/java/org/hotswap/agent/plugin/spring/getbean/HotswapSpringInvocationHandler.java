package org.hotswap.agent.plugin.spring.getbean;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.hotswap.agent.logging.AgentLogger;

/**
 * MethodInterceptor for java.lang.reflect bean Proxies. If the bean inside the proxy is cleared, it will be retrieved
 * from the factory on demand.
 * 
 * @author Erki Ehtla
 * 
 */
public class HotswapSpringInvocationHandler extends DetachableBeanHolder implements InvocationHandler {
	
	public HotswapSpringInvocationHandler(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
		super(bean, beanFactry, paramClasses, paramValues);
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return method.invoke(getBean(), args);
	}
}
