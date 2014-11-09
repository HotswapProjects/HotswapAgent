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
public class HotswapSpringInvocationHandler implements InvocationHandler {
	
	private Object[] paramValues;
	private Object bean;
	private Object beanFactry;
	private Class<?>[] paramClasses;
	private static Collection<WeakReference<HotswapSpringInvocationHandler>> beanProxies = Collections
			.synchronizedSet(new HashSet<WeakReference<HotswapSpringInvocationHandler>>());
	private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapSpringInvocationHandler.class);
	
	public HotswapSpringInvocationHandler(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
		this.bean = bean;
		this.beanFactry = beanFactry;
		this.paramClasses = paramClasses;
		this.paramValues = paramValues;
		beanProxies.add(new WeakReference<HotswapSpringInvocationHandler>(this));
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object beanCopy = bean;
		if (beanCopy == null) {
			Method[] methods = beanFactry.getClass().getMethods();
			for (Method factoryMethod : methods) {
				if (ProxyReplacer.FACTORY_METHOD_NAME.equals(factoryMethod.getName())
						&& Arrays.equals(factoryMethod.getParameterTypes(), paramClasses)) {
					bean = factoryMethod.invoke(beanFactry, paramValues);
					beanCopy = bean;
					LOGGER.info("Bean '{}' loaded", bean.getClass().getName());
					break;
				}
			}
		}
		return method.invoke(beanCopy, args);
	}
	
	public static void clearBeans() {
		int clearCount = 0;
		for (WeakReference<HotswapSpringInvocationHandler> el : beanProxies) {
			HotswapSpringInvocationHandler beanProxy = el.get();
			if (beanProxy != null) {
				beanProxy.bean = null;
				clearCount++;
			}
		}
		LOGGER.info("{} java.lang.reflect proxies reset", clearCount);
	}
}
