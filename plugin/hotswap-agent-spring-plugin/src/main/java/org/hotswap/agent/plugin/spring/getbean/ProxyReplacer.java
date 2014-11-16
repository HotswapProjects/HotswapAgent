package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.hotswap.agent.logging.AgentLogger;

/**
 * Proxies the beans returned by DefaultListableBeanFactory. The beans inside these proxies will be dereferenced when
 * the Spring registry is reset.
 * 
 * @author Erki Ehtla
 * 
 */
public class ProxyReplacer {
	public static final String FACTORY_METHOD_NAME = "getBean";
	private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyReplacer.class);
	
	/**
	 * dereferences the beans inside all of the proxies
	 */
	public static void clearAllProxies() {
		DetachableBeanHolder.detachBeans();
	}
	
	/**
	 * creates a proxied bean
	 * 
	 * @param beanFactry
	 * @param bean
	 * @param classes
	 * @param params
	 * @return
	 */
	public static Object register(Object beanFactry, Object bean, Class<?>[] classes, Object[] params) {
		if (bean.getClass().getName().startsWith("com.sun.proxy.$Proxy")) {
			InvocationHandler handler = new HotswapSpringInvocationHandler(bean, beanFactry, classes, params);
			Object newProxyInstance = Proxy.newProxyInstance(beanFactry.getClass().getClassLoader(), bean.getClass()
					.getInterfaces(), handler);
			return newProxyInstance;
		} else if (EnhancerProxyCreater.isSupportedCglibProxy(bean)) {
			return EnhancerProxyCreater.create(beanFactry, bean, classes, params);
		}
		return bean;
	}
}
