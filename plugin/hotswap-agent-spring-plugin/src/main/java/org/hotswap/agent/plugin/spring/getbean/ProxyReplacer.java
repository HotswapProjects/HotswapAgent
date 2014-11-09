package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import net.sf.cglib.core.DefaultNamingPolicy;
import net.sf.cglib.core.Predicate;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * 
 * @author Erki Ehtla
 * 
 */

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
	
	@OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory", events = LoadEvent.DEFINE)
	public static void replaceBeanWithProxy(CtClass ctClass) throws NotFoundException, CannotCompileException {
		CtMethod[] methods = ctClass.getMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.getName().equals(FACTORY_METHOD_NAME))
				continue;
			StringBuilder strB = new StringBuilder();
			for (CtClass type : ctMethod.getParameterTypes()) {
				strB.append(type.getName()).append(".class").append(", ");
			}
			ctMethod.insertAfter("if(true){return " + ProxyReplacer.class.getName() + ".register($0, $_,new Class[]{"
					+ strB.substring(0, strB.length() - 2) + "}, $args);}");
		}
		
	}
	
	/**
	 * disable cache usage in FastClass.Generator to avoid 'IllegalArgumentException: Protected method' exceptions
	 * 
	 * @param ctClass
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.springframework.cglib.reflect.FastClass.Generator", events = LoadEvent.DEFINE)
	public static void replaceSpringFastClassGenerator(CtClass ctClass) throws NotFoundException,
			CannotCompileException {
		CtConstructor[] constructors = ctClass.getConstructors();
		for (CtConstructor ctConstructor : constructors) {
			ctConstructor.insertAfter("setUseCache(false);");
		}
	}
	
	/**
	 * disable cache usage in FastClass.Generator to avoid 'IllegalArgumentException: Protected method' exceptions
	 * 
	 * @param ctClass
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "net.sf.cglib.reflect.FastClass.Generator", events = LoadEvent.DEFINE)
	public static void replaceCglibFastClassGenerator(CtClass ctClass) throws NotFoundException, CannotCompileException {
		CtConstructor[] constructors = ctClass.getConstructors();
		for (CtConstructor ctConstructor : constructors) {
			ctConstructor.insertAfter("setUseCache(false);");
		}
	}
	
	/**
	 * dereferences the beans inside all of the proxies
	 */
	public static void clearAllProxies() {
		HotswapSpringInvocationHandler.clearBeans();
		HotswapSpringCallback.clearBeans();
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
			Object newProxyInstance = Proxy.newProxyInstance(ProxyReplacer.class.getClassLoader(), bean.getClass()
					.getInterfaces(), handler);
			return newProxyInstance;
		} else if (bean.getClass().getName().contains("CGLIB$$")) {
			Callback handler = new HotswapSpringCallback(bean, beanFactry, classes, params);
			Enhancer e = new Enhancer();
			e.setUseCache(true);
			Class[] proxyInterfaces = new Class<?>[bean.getClass().getInterfaces().length];
			Class[] classInterfaces = bean.getClass().getInterfaces();
			for (int i = 0; i < classInterfaces.length; i++) {
				proxyInterfaces[i] = classInterfaces[i];
			}
			
			e.setInterfaces(proxyInterfaces);
			e.setSuperclass(bean.getClass().getSuperclass());
			e.setCallback(handler);
			e.setCallbackType(HotswapSpringCallback.class);
			e.setNamingPolicy(new DefaultNamingPolicy() {
				@Override
				public String getClassName(String prefix, String source, Object key, Predicate names) {
					return super.getClassName("HOTSWAPAGENT_" + prefix, source, key, names);
				}
			});
			
			Object create = e.create();
			return create;
		}
		return bean;
	}
}
