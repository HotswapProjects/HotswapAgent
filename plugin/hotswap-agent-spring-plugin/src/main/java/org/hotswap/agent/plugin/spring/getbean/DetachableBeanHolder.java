package org.hotswap.agent.plugin.spring.getbean;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
	private static List<WeakReference<DetachableBeanHolder>> beanProxies = Collections
			.synchronizedList(new ArrayList<WeakReference<DetachableBeanHolder>>());
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
			LOGGER.info("{} Spring proxies reset", i);
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
					// It migt be the same object (if factory returns same object - meaning
					//   that although clearAllProxies() was called, this bean did not change)
					// Unwrap the target bean, it is always available
					// see org.hotswap.agent.plugin.spring.getbean.EnhancerProxyCreater.create()
					if (freshBean instanceof SpringHotswapAgentProxy) {
						freshBean = ((SpringHotswapAgentProxy) freshBean).$$ha$getTarget();
					}

					bean = freshBean;
					beanCopy = bean;
					LOGGER.info("Bean '{}' loaded", bean.getClass().getName());
					break;
				}
			}
		}
		return beanCopy;
	}
	
	protected boolean isBeanLoaded(){
		return bean != null;
	}
}