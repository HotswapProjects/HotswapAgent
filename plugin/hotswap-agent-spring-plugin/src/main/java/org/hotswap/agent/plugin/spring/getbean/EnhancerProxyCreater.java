package org.hotswap.agent.plugin.spring.getbean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

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
	private static EnhancerProxyCreater INSTANCE;
	public static final String SPRING_PACKAGE = "org.springframework.cglib.";
	public static final String CGLIB_PACKAGE = "net.sf.cglib.";
	
	private Class<?> springProxy;
	private Class<?> springCallback;
	private Class<?> springNamingPolicy;
	private Method createSpringProxy;
	private Class<?> cglibProxy;
	private Class<?> cglibCallback;
	private Class<?> cglibNamingPolicy;
	private Method createCglibProxy;
	
	private Object springLock = new Object();
	private Object cglibLock = new Object();
	private final ClassLoader loader;
	private final ProtectionDomain pd;
	
	public EnhancerProxyCreater(ClassLoader loader, ProtectionDomain pd) {
		super();
		this.loader = loader;
		this.pd = pd;
	}
	
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
	public static Object createProxy(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
		if (INSTANCE == null) {
			INSTANCE = new EnhancerProxyCreater(bean.getClass().getClassLoader(), bean.getClass().getProtectionDomain());
		}
		return INSTANCE.create(beanFactry, bean, paramClasses, paramValues);
	}
	
	private Object create(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
		try {
			Method proxyCreater = getProxyCreationMethod(bean);
			if (proxyCreater == null) {
				return bean;
			}
			return proxyCreater.invoke(null, beanFactry, bean, paramClasses, paramValues);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | CannotCompileException
				| NotFoundException e) {
			LOGGER.error("Creating a proxy failed", e);
			throw new RuntimeException(e);
		}
	}
	
	private Method getProxyCreationMethod(Object bean) throws CannotCompileException, NotFoundException {
		String beanClassName = bean.getClass().getName();
		if (beanClassName.contains("$$EnhancerBySpringCGLIB")) {
			if (createSpringProxy == null) {
				synchronized (springLock) {
					if (createSpringProxy == null) {
						ClassPool cp = getCp(loader);
						springCallback = buildProxyCallbackClass(SPRING_PACKAGE, cp);
						springNamingPolicy = buildNamingPolicyClass(SPRING_PACKAGE, cp);
						springProxy = buildProxyCreaterClass(SPRING_PACKAGE, springCallback, springNamingPolicy, cp);
						createSpringProxy = springProxy.getDeclaredMethods()[0];
					}
				}
			}
			return createSpringProxy;
		} else if (beanClassName.contains("$$EnhancerByCGLIB")) {
			if (createCglibProxy == null) {
				synchronized (cglibLock) {
					if (createCglibProxy == null) {
						ClassPool cp = getCp(loader);
						cglibCallback = buildProxyCallbackClass(CGLIB_PACKAGE, cp);
						cglibNamingPolicy = buildNamingPolicyClass(CGLIB_PACKAGE, cp);
						cglibProxy = buildProxyCreaterClass(CGLIB_PACKAGE, cglibCallback, cglibNamingPolicy, cp);
						createCglibProxy = cglibProxy.getDeclaredMethods()[0];
					}
				}
			}
			return createSpringProxy;
		} else {
			LOGGER.error("Unable to determine the location of the Cglib package");
			return null;
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
	 * @param cglibNamingPolicy2
	 * @param cglibCallback2
	 * @param callback
	 *            Callback class used for Enhancer
	 * @param namingPolicy
	 *            NamingPolicy class used for Enhancer
	 * @param cp
	 * @param classLoader
	 * @return Class that creates proxies via method "public static Object create(Object beanFactry, Object bean,
	 *         Class[] classes, Object[] params)"
	 * @throws CannotCompileException
	 */
	private Class<?> buildProxyCreaterClass(String cglibPackage, Class<?> callback, Class<?> namingPolicy, ClassPool cp)
			throws CannotCompileException {
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
		String body = rawBody.replaceAll("\\{0\\}", proxy).replaceAll("\\{1\\}", core)
				.replaceAll("\\{2\\}", callback.getName()).replaceAll("\\{3\\}", namingPolicy.getName());
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass(loader, pd);
	}
	
	/**
	 * Creates a NamingPolicy for usage in buildProxyCreaterClass. Eventually a instance of this class will be used as
	 * an argument for an Enhancer instances setNamingPolicy method. Classname prefix for proxies will be HOTSWAPAGENT_
	 * 
	 * @param cglibPackage
	 *            Cglib Package name
	 * @param cp
	 * @return DefaultNamingPolicy sublass
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	private Class<?> buildNamingPolicyClass(String cglibPackage, ClassPool cp) throws CannotCompileException,
			NotFoundException {
		CtClass ct = cp.makeClass("HotswapAgentSpringNamingPolicy" + getClassSuffix(cglibPackage));
		String core = cglibPackage + "core.";
		String originalNamingPolicy = core + "SpringNamingPolicy";
		if (cp.find(originalNamingPolicy) == null)
			originalNamingPolicy = core + "DefaultNamingPolicy";
		ct.setSuperclass(cp.get(originalNamingPolicy));
		String rawBody = "			public String getClassName(String prefix, String source, Object key, {0}Predicate names) {"//
				+ "				return super.getClassName(\"HOTSWAPAGENT_\" + prefix, source, key, names);"//
				+ "			}";
		String body = rawBody.replaceAll("\\{0\\}", core);
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass(loader, pd);
	}
	
	private static String getClassSuffix(String cglibPackage) {
		return String.valueOf(cglibPackage.hashCode()).replace("-", "_");
	}
	
	/**
	 * Creates a Cglib Callback which is a subclass of DetachableBeanHolder
	 * 
	 * @param cglibPackage
	 *            Cglib Package name
	 * @param cp
	 * @return Class of the Enhancer Proxy callback
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	private Class<?> buildProxyCallbackClass(String cglibPackage, ClassPool cp) throws CannotCompileException,
			NotFoundException {
		String proxyPackage = cglibPackage + "proxy.";
		CtClass ct = cp.makeClass("HotswapSpringCallback" + getClassSuffix(cglibPackage));
		ct.setSuperclass(cp.get(DetachableBeanHolder.class.getName()));
		ct.addInterface(cp.get(proxyPackage + "MethodInterceptor"));
		
		String rawBody = "	public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args, {0}MethodProxy proxy) throws Throwable {"//
				+ "		if(method != null && method.getName().equals(\"finalize\") && method.getParameterTypes().length == 0)" //
				+ "			return null;" //
				+ "		return proxy.invoke(getBean(), args);" //
				+ "	}";
		String body = rawBody.replaceAll("\\{0\\}", proxyPackage);
		
		CtMethod m = CtNewMethod.make(body, ct);
		ct.addMethod(m);
		return ct.toClass(loader, pd);
	}
	
	private ClassPool getCp(ClassLoader loader) {
		ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new LoaderClassPath(loader));
		return cp;
	}
}
