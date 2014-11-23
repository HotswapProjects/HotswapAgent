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
 * Creates a Cglib proxy instance along with the neccessary Callback classes. Uses either the repackaged version of
 * Cglib (Spring >= 3.2) or the stand-alone version (Spring < 3.2).
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
	private static volatile Method createSpringProxy;
	private static Class<?> cglibProxy;
	private static Class<?> cglibCallback;
	private static Class<?> cglibNamingPolicy;
	private static volatile Method createCglibProxy;
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
	
	/**
	 * Creates a Cglib proxy instance along with the neccessary Callback classes, if those have not been created
	 * already. Uses either the repackaged version of Cglib (Spring >= 3.2) or the stand-alone version (Spring < 3.2).
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
	 * @return
	 */
	public static Object create(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
		try {
			String beanClassName = bean.getClass().getName();
			if (beanClassName.contains("$$EnhancerBySpringCGLIB")) {
				if (createSpringProxy == null) {
					synchronized (springLock) {
						if (createSpringProxy == null) {
							ClassPool cp = getCp(beanFactry.getClass().getClassLoader());
							springCallback = buildProxyCallbackClass(SPRING_PACKAGE, cp);
							springNamingPolicy = buildNamingPolicyClass(SPRING_PACKAGE, cp);
							springProxy = buildProxyCreaterClass(SPRING_PACKAGE, springCallback.getName(),
									springNamingPolicy.getName(), cp);
							createSpringProxy = springProxy.getDeclaredMethods()[0];
							cp = null;
						}
					}
				}
				return createSpringProxy.invoke(null, beanFactry, bean, paramClasses, paramValues);
			} else if (beanClassName.contains("$$EnhancerByCGLIB")) {
				if (createCglibProxy == null) {
					synchronized (cglibLock) {
						if (createCglibProxy == null) {
							ClassPool cp = getCp(beanFactry.getClass().getClassLoader());
							cglibCallback = buildProxyCallbackClass(CGLIB_PACKAGE, cp);
							cglibNamingPolicy = buildNamingPolicyClass(CGLIB_PACKAGE, cp);
							cglibProxy = buildProxyCreaterClass(CGLIB_PACKAGE, cglibCallback.getName(),
									cglibNamingPolicy.getName(), cp);
							createCglibProxy = cglibProxy.getDeclaredMethods()[0];
							cp = null;
						}
					}
				}
				return createCglibProxy.invoke(null, beanFactry, bean, paramClasses, paramValues);
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
	
	/**
	 * Builds a class that has a single public static method create(Object beanFactry, Object bean, Class[] classes,
	 * Object[] params). The method of the created class returns a Cglib Enhancer created proxy of the parameter bean.
	 * The proxy has single callback, whish is a subclass of DetachableBeanHolder. Classname prefix for created proxies
	 * will be HOTSWAPAGENT_
	 * 
	 * @param cglibPackage
	 *            Cglib Package name
	 * @param callback
	 *            Callback class used for Enhancer
	 * @param namingPolicy
	 *            NamingPolicy class used for Enhancer
	 * @return Class that creates proxies via method "public static Object create(Object beanFactry, Object bean,
	 *         Class[] classes, Object[] params)"
	 * @throws CannotCompileException
	 */
	private static Class<?> buildProxyCreaterClass(String cglibPackage, String callback, String namingPolicy,
			ClassPool cp) throws CannotCompileException {
		CtClass ct = cp.makeClass("HotswapAgentSpringBeanProxy" + getClassSuffix(cglibPackage));
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
				.replaceAll("\\{3\\}", namingPolicy);
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass();
	}
	
	/**
	 * Creates a NamingPolicy for usage in buildProxyCreaterClass. Eventually a instance of this class will be used as
	 * an argument for an Enhancer instances setNamingPolicy method. Classname prefix for proxies will be HOTSWAPAGENT_
	 * 
	 * @param cglibPackage
	 *            Cglib Package name
	 * @return DefaultNamingPolicy sublass
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	private static Class<?> buildNamingPolicyClass(String cglibPackage, ClassPool cp) throws CannotCompileException,
			NotFoundException {
		CtClass ct = cp.makeClass("HotswapAgentSpringNamingPolicy" + getClassSuffix(cglibPackage));
		String core = cglibPackage + "core.";
		ct.setSuperclass(cp.get(core + "DefaultNamingPolicy"));
		String rawBody = "			public String getClassName(String prefix, String source, Object key, {0}Predicate names) {"//
				+ "				return super.getClassName(\"HOTSWAPAGENT_\" + prefix, source, key, names);"//
				+ "			}";
		String body = rawBody.replaceAll("\\{0\\}", core);
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass();
	}
	
	private static String getClassSuffix(String cglibPackage) {
		return String.valueOf(cglibPackage.hashCode()).replace("-", "_");
	}
	
	/**
	 * Creates a Cglib Callback which is a subclass of DetachableBeanHolder
	 * 
	 * @param cglibPackage
	 *            Cglib Package name
	 * @return Class of the Enhancer Proxy callback
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	private static Class<?> buildProxyCallbackClass(String cglibPackage, ClassPool cp) throws CannotCompileException,
			NotFoundException {
		String proxyPackage = cglibPackage + "proxy.";
		CtClass ct = cp.makeClass("HotswapSpringCallback" + getClassSuffix(cglibPackage));
		ct.setSuperclass(cp.get(DetachableBeanHolder.class.getName()));
		ct.addInterface(cp.get(proxyPackage + "MethodInterceptor"));
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
	
	private static ClassPool getCp(ClassLoader loader) {
		if (cp == null) {
			synchronized (cpLock) {
				if (cp == null) {
					cp = new ClassPool();
					cp.appendSystemPath();
					cp.appendClassPath(new LoaderClassPath(loader));
					return cp;
				}
			}
		}
		return cp;
	}
}
