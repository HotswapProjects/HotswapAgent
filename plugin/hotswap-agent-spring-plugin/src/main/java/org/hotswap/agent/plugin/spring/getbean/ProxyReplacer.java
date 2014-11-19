package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Proxies the beans. The beans inside these proxies can be cleared.
 * 
 * @author Erki Ehtla
 * 
 */
public class ProxyReplacer {
	/**
	 * Name of the Spring beanFactory method, which returns a bean
	 */
	public static final String FACTORY_METHOD_NAME = "getBean";
	
	/**
	 * Clears the bean references inside all the proxies
	 */
	public static void clearAllProxies() {
		DetachableBeanHolder.detachBeans();
	}
	
	/**
	 * Creates a proxied Spring bean
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
	 * @return Proxied bean
	 */
	public static Object register(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
		if (bean.getClass().getName().startsWith("com.sun.proxy.$Proxy")) {
			InvocationHandler handler = new HotswapSpringInvocationHandler(bean, beanFactry, paramClasses, paramValues);
			return Proxy.newProxyInstance(beanFactry.getClass().getClassLoader(), bean.getClass().getInterfaces(),
					handler);
		} else if (EnhancerProxyCreater.isSupportedCglibProxy(bean)) {
			return EnhancerProxyCreater.create(beanFactry, bean, paramClasses, paramValues);
		}
		return bean;
	}
}
