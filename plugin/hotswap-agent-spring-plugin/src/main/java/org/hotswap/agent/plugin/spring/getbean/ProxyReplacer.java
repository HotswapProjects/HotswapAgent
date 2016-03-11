package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.hotswap.agent.logging.AgentLogger;

/**
 * Proxies the beans. The beans inside these proxies can be cleared.
 * 
 * @author Erki Ehtla
 * 
 */
public class ProxyReplacer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyReplacer.class);
	private static Class<?> infrastructureProxyClass;
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
	 * Creates a proxied Spring bean. Called from within WebApp code by modification of Spring classes
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
			Class<?>[] interfaces = bean.getClass().getInterfaces();
			try {
				if (!Arrays.asList(interfaces).contains(getInfrastructureProxyClass())) {
					interfaces = Arrays.copyOf(interfaces, interfaces.length + 1);
					interfaces[interfaces.length - 1] = getInfrastructureProxyClass();
				}
			} catch (ClassNotFoundException e) {
				LOGGER.error("error adding org.springframework.core.InfrastructureProxy to proxy class", e);
			}
			return Proxy.newProxyInstance(beanFactry.getClass().getClassLoader(), interfaces, handler);
		} else if (EnhancerProxyCreater.isSupportedCglibProxy(bean)) {
			return EnhancerProxyCreater.createProxy(beanFactry, bean, paramClasses, paramValues);
		}
		return bean;
	}

	private static Class<?> getInfrastructureProxyClass() throws ClassNotFoundException {
		if (infrastructureProxyClass == null) {
			infrastructureProxyClass = ProxyReplacer.class.getClassLoader().loadClass("org.springframework.core.InfrastructureProxy");
		}
		return infrastructureProxyClass;
	}
}
