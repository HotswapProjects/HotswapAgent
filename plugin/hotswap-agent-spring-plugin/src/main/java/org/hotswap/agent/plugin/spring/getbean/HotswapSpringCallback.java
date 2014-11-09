package org.hotswap.agent.plugin.spring.getbean;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.hotswap.agent.logging.AgentLogger;

/**
 * MethodInterceptor for Cglib bean Proxies. If the bean inside the proxy is cleared, it will be retrieved from the
 * factory on demand.
 * 
 * @author Erki Ehtla
 * 
 */
public class HotswapSpringCallback implements MethodInterceptor {
	
	private Object bean;
	private Object beanFactory;
	private Class<?>[] paramClasses;
	private Object[] paramValues;
	private static Collection<WeakReference<HotswapSpringCallback>> beanProxies = Collections
			.synchronizedSet(new HashSet<WeakReference<HotswapSpringCallback>>());
	private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapSpringCallback.class);
	
	public HotswapSpringCallback(Object bean, Object beanFactry, Class<?>[] paramClasses, Object[] paramValues) {
		this.bean = bean;
		this.beanFactory = beanFactry;
		this.paramClasses = paramClasses;
		this.paramValues = paramValues;
		beanProxies.add(new WeakReference<HotswapSpringCallback>(this));
	}
	
	public static void clearBeans() {
		int clearCount = 0;
		for (WeakReference<HotswapSpringCallback> el : beanProxies) {
			HotswapSpringCallback hotswapSpringCallback = el.get();
			if (hotswapSpringCallback != null) {
				hotswapSpringCallback.bean = null;
				clearCount++;
			}
		}
		LOGGER.info("{} Cglib proxies reset", clearCount);
	}
	
	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
		Object beanCopy = bean;
		if (beanCopy == null) {
			Method[] methods = beanFactory.getClass().getMethods();
			for (Method factoryMethod : methods) {
				if (ProxyReplacer.FACTORY_METHOD_NAME.equals(factoryMethod.getName())
						&& Arrays.equals(factoryMethod.getParameterTypes(), paramClasses)) {
					bean = factoryMethod.invoke(beanFactory, paramValues);
					beanCopy = bean;
					LOGGER.info("Bean '{}' loaded", bean.getClass().getName());
					break;
				}
			}
		}
		return proxy.invoke(beanCopy, args);
	}
}
