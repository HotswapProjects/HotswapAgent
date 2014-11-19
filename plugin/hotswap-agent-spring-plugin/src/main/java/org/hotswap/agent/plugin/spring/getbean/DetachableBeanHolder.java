package org.hotswap.agent.plugin.spring.getbean;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.hotswap.agent.logging.AgentLogger;

/**
 * 
 * Loadable detachable Spring bean holder
 * 
 * @author Erki Ehtla
 * 
 */
public class DetachableBeanHolder {
	
	private Object bean;
	private Object beanFactory;
	private Class<?>[] paramClasses;
	private Object[] paramValues;
	private static Collection<WeakReference<DetachableBeanHolder>> beanProxies = Collections
			.synchronizedSet(new HashSet<WeakReference<DetachableBeanHolder>>());
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
		this.bean = bean;
		this.beanFactory = beanFactry;
		this.paramClasses = paramClasses;
		this.paramValues = paramValues;
		beanProxies.add(new WeakReference<DetachableBeanHolder>(this));
	}
	
	/**
	 * Clears the bean references inside all of the proxies
	 */
	public static void detachBeans() {
		int clearCount = 0;
		for (WeakReference<DetachableBeanHolder> el : beanProxies) {
			DetachableBeanHolder hotswapSpringCallback = el.get();
			if (hotswapSpringCallback != null) {
				hotswapSpringCallback.detach();
				clearCount++;
			}
		}
		LOGGER.info("{} Spring proxies reset", clearCount);
	}
	
	/**
	 * Clear the bean for this proxy
	 */
	public void detach() {
		bean = null;
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
					bean = factoryMethod.invoke(beanFactory, paramValues);
					beanCopy = bean;
					LOGGER.info("Bean '{}' loaded", bean.getClass().getName());
					break;
				}
			}
		}
		return beanCopy;
	}
	
}