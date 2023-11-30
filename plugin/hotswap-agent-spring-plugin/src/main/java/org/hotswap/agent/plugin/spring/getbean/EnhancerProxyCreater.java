/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.getbean;

import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.springframework.core.SpringVersion;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.WeakHashMap;

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

    final private Map<Object, Object> beanProxies = new WeakHashMap<>();

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
     * @param beanFactry Spring beanFactory
     * @param bean Spring bean
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
        Object proxyBean = null;
        if (beanProxies.containsKey(bean)) {
            proxyBean = beanProxies.get(bean);
        } else {
            synchronized (beanProxies) {
                if (beanProxies.containsKey(bean)) {
                    proxyBean = bean;
                } else {
                    proxyBean = doCreate(beanFactry, bean, paramClasses, paramValues);
                }
                beanProxies.put(bean, proxyBean);
            }
        }

        // in case of HA proxy set the target. It might be cleared by clearProxies
        //   but the underlying bean did not change. We need this to resolve target bean
        //   in org.hotswap.agent.plugin.spring.getbean.DetachableBeanHolder.getBean()
        if (proxyBean instanceof SpringHotswapAgentProxy) {
            ((SpringHotswapAgentProxy) proxyBean).$$ha$setTarget(bean);
        }

        return proxyBean;
    }

    private Object doCreate(Object beanFactry, Object bean, Class<?>[] paramClasses, Object[] paramValues) {
        try {
            Method proxyCreater = getProxyCreationMethod(bean);
            if (proxyCreater == null) {
                return bean;
            } else {
                return proxyCreater.invoke(null, beanFactry, bean, paramClasses, paramValues);
            }
        } catch (IllegalArgumentException | InvocationTargetException e) {
            LOGGER.warning("Can't create proxy for " + bean.getClass().getSuperclass()
                    + " because there is no default constructor,"
                    + " which means your non-singleton bean created before won't get rewired with new props when update class.");
            return bean;
        } catch (IllegalAccessException | CannotCompileException | NotFoundException e) {
            LOGGER.error("Creating a proxy failed", e);
            throw new RuntimeException(e);
        }
    }

    private Method getProxyCreationMethod(Object bean) throws CannotCompileException, NotFoundException {
        if (getCp(loader).find("org.springframework.cglib.proxy.MethodInterceptor") != null) {
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
        } else if (getCp(loader).find("net.sf.cglib.proxy.MethodInterceptor") != null) {
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
            return createCglibProxy;
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
     * @param cglibPackage  Cglib Package name
     * @param callback  Callback class used for Enhancer
     * @param namingPolicy  NamingPolicy class used for Enhancer
     * @param cp
     * @return Class that creates proxies via method "public static Object create(Object beanFactry, Object bean,
     *         Class[] classes, Object[] params)"
     * @throws CannotCompileException
     */
    private Class<?> buildProxyCreaterClass(String cglibPackage, Class<?> callback, Class<?> namingPolicy, ClassPool cp)
            throws CannotCompileException {
        CtClass ct = cp.makeClass("HotswapAgentSpringBeanProxy" + getClassSuffix(cglibPackage));
        String proxy = cglibPackage + "proxy.";
        String core = cglibPackage + "core.";
        String rawBody =
                "public static Object create(Object beanFactry, Object bean, Class[] classes, Object[] params) {" +
                        "{2} handler = new {2}(bean, beanFactry, classes, params);" +
                        "{0}Enhancer e = new {0}Enhancer();" +
                        "e.setUseCache(false);" +
                        "Class[] proxyInterfaces = new Class[bean.getClass().getInterfaces().length+1];" +
                        "Class[] classInterfaces = bean.getClass().getInterfaces();" +
                        "for (int i = 0; i < classInterfaces.length; i++) {" +
                            "proxyInterfaces[i] = classInterfaces[i];" +
                         "}" +
                         "proxyInterfaces[proxyInterfaces.length-1] = org.hotswap.agent.plugin.spring.getbean.SpringHotswapAgentProxy.class;" +
                         "e.setInterfaces(proxyInterfaces);" +
                         "e.setSuperclass(bean.getClass().getSuperclass());" +
                         "e.setNamingPolicy(new {3}());" +
                         "e.setCallbackType({2}.class);" +
                         tryObjenesisProxyCreation(cp) +
                         "e.setCallback(handler);" +
                         "return e.create();" +
                "}";
        String body = rawBody
                .replaceAll("\\{0\\}", proxy)
                .replaceAll("\\{1\\}", core)
                .replaceAll("\\{2\\}", callback.getName())
                .replaceAll("\\{3\\}", namingPolicy.getName());
        CtMethod m = CtNewMethod.make(body, ct);
        ct.addMethod(m);
        return ct.toClass(loader, pd);
    }

    // Spring 4: CGLIB-based proxy classes no longer require a default constructor. Support is provided
    // via the objenesis library which is repackaged inline and distributed as part of the Spring Framework.
    // With this strategy, no constructor at all is being invoked for proxy instances anymore.
    // http://blog.codeleak.pl/2014/07/spring-4-cglib-based-proxy-classes-with-no-default-ctor.html
    //
    // If objenesis is not available (pre Spring 4), only beans with default constructor may by proxied
    private String tryObjenesisProxyCreation(ClassPool cp) {
        if (cp.find("org.springframework.objenesis.SpringObjenesis") == null) {
            return "";
        }

        // do not know why 4.2.6 AND 4.3.0 does not work, probably cglib version and cache problem
        if (SpringVersion.getVersion().startsWith("4.2.6") ||
                SpringVersion.getVersion().startsWith("4.3.0")) {
            return "";
        }

        return
                "org.springframework.objenesis.SpringObjenesis objenesis = new org.springframework.objenesis.SpringObjenesis();" +
                    "if (objenesis.isWorthTrying()) {" +
//                      "try {" +
                            "Class proxyClass = e.createClass();" +
                            "Object proxyInstance = objenesis.newInstance(proxyClass, false);" +
                            "((org.springframework.cglib.proxy.Factory) proxyInstance).setCallbacks(new org.springframework.cglib.proxy.Callback[] {handler});" +
                            "return proxyInstance;" +
//                      "}" +
//                      "catch (Throwable ex) {}" +
                    "}";
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
    private Class<?> buildNamingPolicyClass(String cglibPackage, ClassPool cp) throws CannotCompileException, NotFoundException {
        CtClass ct = cp.makeClass("HotswapAgentSpringNamingPolicy" + getClassSuffix(cglibPackage));
        String core = cglibPackage + "core.";
        String originalNamingPolicy = core + "SpringNamingPolicy";
        if (cp.find(originalNamingPolicy) == null)
            originalNamingPolicy = core + "DefaultNamingPolicy";
        ct.setSuperclass(cp.get(originalNamingPolicy));
        String rawBody =
                "public String getClassName(String prefix, String source, Object key, {0}Predicate names) {" +
                        "return super.getClassName(prefix + \"$HOTSWAPAGENT_\", source, key, names);" +
                "}";
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
     * @param cglibPackage  Cglib Package name
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

        String rawBody =
                "public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args, {0}MethodProxy proxy) throws Throwable {" +
                    "if(method != null && method.getName().equals(\"finalize\") && method.getParameterTypes().length == 0) {" +
                        "return null;" +
                    "}" +
                    "if(method != null && method.getName().equals(\"$$ha$getTarget\")) {" +
                        "return getTarget();" +
                    "}" +
                    "if(method != null && method.getName().equals(\"$$ha$setTarget\")) {" +
                        "setTarget(args[0]); return null;" +
                    "}" +
                    "return proxy.invoke(getBean(), args);" +
                "}";
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