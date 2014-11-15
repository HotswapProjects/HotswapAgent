package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Creates a Proxy along with the neccessary classes with either the repackaged version Cglib (Spring >= 3.2) or the
 * stand-alone version (Spring < 3.2).
 * 
 * @author Erki Ehtla
 * 
 */
public class EnhancerProxyCreater {
	private static AgentLogger LOGGER = AgentLogger.getLogger(EnhancerProxyCreater.class);
	public static final String SPRING_PACKAGE = "org.springframework.cglib.";
	public static final String CGLIB_PACKAGE = "net.sf.cglib.";
	private static Class<?> springProxy;
	private static Class<?> springCallback;
	private static Class<?> springNamingPolicy;
	private static Method createSpringProxy;
	private static Class<?> cglibProxy;
	private static Class<?> cglibCallback;
	private static Class<?> cglibNamingPolicy;
	private static Method createCglibProxy;
	private static Object springLock = new Object();
	private static Object cglibLock = new Object();
	private static Object cpLock = new Object();
	private static volatile ClassPool cp;
	
	public static boolean isSupportedCglibProxy(Object bean) {
		if (bean == null) {
			return false;
		}
		String beanClassName = bean.getClass().getName();
		return beanClassName.contains("$$EnhancerBySpringCGLIB") || beanClassName.contains("$$EnhancerByCGLIB");
	}
	
	public static Object create(Object beanFactry, Object bean, Class<?>[] classes, Object[] params) {
		try {
			String beanClassName = bean.getClass().getName();
			if (beanClassName.contains("$$EnhancerBySpringCGLIB")) {
				if (createSpringProxy == null) {
					synchronized (springLock) {
						if (createSpringProxy == null) {
							springCallback = buildProxyCallbackClass(SPRING_PACKAGE);
							springNamingPolicy = buildNamingPolicyClass(SPRING_PACKAGE);
							springProxy = buildProxyCreaterClass(SPRING_PACKAGE, springCallback.getName(),
									springNamingPolicy.getName());
							createSpringProxy = springProxy.getDeclaredMethods()[0];
							cp = null;
						}
					}
				}
				return createSpringProxy.invoke(null, beanFactry, bean, classes, params);
			} else if (beanClassName.contains("$$EnhancerByCGLIB")) {
				if (createCglibProxy == null) {
					synchronized (cglibLock) {
						if (createCglibProxy == null) {
							cglibCallback = buildProxyCallbackClass(CGLIB_PACKAGE);
							cglibNamingPolicy = buildNamingPolicyClass(CGLIB_PACKAGE);
							cglibProxy = buildProxyCreaterClass(CGLIB_PACKAGE, cglibCallback.getName(),
									cglibNamingPolicy.getName());
							createCglibProxy = cglibProxy.getDeclaredMethods()[0];
							cp = null;
						}
					}
				}
				return createCglibProxy.invoke(null, beanFactry, bean, classes, params);
			} else {
				LOGGER.error("Unable to determine the location of the Cglib package");
				return bean;
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | CannotCompileException
				| NotFoundException e) {
			LOGGER.error("Creating a proxy failed", e);
			throw new RuntimeException(e);
		}
	}
	
	private static Class<?> buildProxyCreaterClass(String cglibPackage, String callback, String namingPolciy)
			throws CannotCompileException {
		CtClass ct = getCp().makeClass("HotswapAgentSpringBeanProxy" + Math.abs(cglibPackage.hashCode()));
		String proxy = cglibPackage + "proxy.";
		String core = cglibPackage + "core.";
		String rawBody = "public static Object create(Object beanFactry, Object bean, Class[] classes, Object[] params) {"
				+ "{2} handler = new {2}(bean, beanFactry, classes, params);"//
				+ "		{0}Enhancer e = new {0}Enhancer();"//
				+ "		e.setUseCache(true);"//
				+ "		Class[] proxyInterfaces = new Class[bean.getClass().getInterfaces().length];"//
				+ "		Class[] classInterfaces = bean.getClass().getInterfaces();"//
				+ "		for (int i = 0; i < classInterfaces.length; i++) {"//
				+ "			proxyInterfaces[i] = classInterfaces[i];"//
				+ "		}"//
				+ "		e.setInterfaces(proxyInterfaces);"//
				+ "		e.setSuperclass(bean.getClass().getSuperclass());"//
				+ "		e.setCallback(handler);"//
				+ "		e.setCallbackType({2}.class);"//
				+ "		e.setNamingPolicy(new {3}());"//
				+ "		return e.create();"//
				+ "	}";
		String body = rawBody.replaceAll("\\{0\\}", proxy).replaceAll("\\{1\\}", core).replaceAll("\\{2\\}", callback)
				.replaceAll("\\{3\\}", namingPolciy);
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass();
	}
	
	private static Class<?> buildNamingPolicyClass(String cglibPackage) throws CannotCompileException,
			NotFoundException {
		CtClass ct = getCp().makeClass("HotswapAgentSpringNamingPolicy" + Math.abs(cglibPackage.hashCode()));
		String core = cglibPackage + "core.";
		ct.setSuperclass(getCp().get(core + "DefaultNamingPolicy"));
		String rawBody = "			public String getClassName(String prefix, String source, Object key, {0}Predicate names) {"//
				+ "				return super.getClassName(\"HOTSWAPAGENT_\" + prefix, source, key, names);"//
				+ "			}";
		String body = rawBody.replaceAll("\\{0\\}", core);
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass();
	}
	
	private static Class<?> buildProxyCallbackClass(String cglibPackage) throws CannotCompileException,
			NotFoundException {
		String proxyPackage = cglibPackage + "proxy.";
		String callbackClassName = "HotswapSpringCallback" + Math.abs(cglibPackage.hashCode());
		CtClass ct = getCp().makeClass(callbackClassName);
		ct.setSuperclass(getCp().get(DetachableBeanHolder.class.getName()));
		ct.addInterface(getCp().get(proxyPackage + "MethodInterceptor"));
		// String constructor = "public " + callbackClassName
		// + "(Object bean, Object beanFactry, Class[] paramClasses, Object[] paramValues) {"//
		// + "		super(bean, beanFactry, paramClasses, paramValues);"//
		// + "	}";
		// CtNewConstructor.make(constructor, ct);
		
		String rawBody = "	public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args, {0}MethodProxy proxy) throws Throwable {"//
				+ "		return proxy.invoke(getBean(), args);" //
				+ "	}";
		String body = rawBody.replaceAll("\\{0\\}", proxyPackage);
		
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass();
	}
	
	private static ClassPool getCp() {
		if (cp == null) {
			synchronized (cpLock) {
				if (cp == null) {
					cp = new ClassPool();
					cp.appendSystemPath();
					cp.appendClassPath(new LoaderClassPath(EnhancerProxyCreater.class.getClassLoader()));
					return cp;
				}
			}
		}
		return cp;
	}
}
