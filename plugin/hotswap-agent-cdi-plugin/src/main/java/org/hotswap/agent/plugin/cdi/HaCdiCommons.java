/*
 * Copyright 2013-2019 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Helper class for common names definition for CDI plugins
 */
public class HaCdiCommons {

    private static AgentLogger LOGGER = AgentLogger.getLogger(HaCdiCommons.class);

    private static final String BEAN_REGISTRY_FIELD = "$$ha$beanRegistry";
    private static final Map<Class<? extends Annotation>, Class<? extends Context>> scopeToContextMap = new HashMap<>();
    private static Map<HaCdiExtraContext, Boolean> extraContexts = new HashMap<>();

    public static boolean isInExtraScope(Bean<?> bean) {
        Class<?> beanClass = bean.getBeanClass();
        for (HaCdiExtraContext extraContext: extraContexts.keySet()) {
            if (extraContext.containsBeanInstances(beanClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add bean registry field to context, register bean instances in get(...) methods
     *
     * @param classPool the class pool
     * @param ctClass the ct class
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException the not found exception
     */
    public static void transformContext(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        addBeanRegistryToContext(classPool, ctClass);
        transformGet1(classPool, ctClass);
        transformGet2(classPool, ctClass);
        LOGGER.debug(ctClass.getName() + " - patched by bean registration.");
    }

    /**
     * Adds the bean registry to context.
     *
     * @param classPool the class pool
     * @param ctClass the ct class
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException the not found exception
     */
    public static void addBeanRegistryToContext(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtField beanRegistryFld = CtField.make(
            "public static java.util.Map " + BEAN_REGISTRY_FIELD + ";" , ctClass
        );
        ctClass.addField(beanRegistryFld);
    }

    /**
     * Transform 1 argument get method :
     *    <code>public <T> T get(Contextual<T> contextual);</code>
     *
     * @param classPool the class pool
     * @param ctClass the ct class
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException the not found exception
     */
    public static void transformGet1(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod methGet1 = ctClass.getDeclaredMethod("get", new CtClass[] {
            classPool.get("javax.enterprise.context.spi.Contextual")
        });

        methGet1.insertAfter(getRegistrationCode());
    }

    /**
     * Transform  2 arguments get method :
     *    <code>public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext);</code>
     *
     * @param classPool the class pool
     * @param ctClass the ct class
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException the not found exception
     */
    public static void transformGet2(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod methGet2 = ctClass.getDeclaredMethod("get", new CtClass[] {
            classPool.get("javax.enterprise.context.spi.Contextual"),
            classPool.get("javax.enterprise.context.spi.CreationalContext"),
        });

        methGet2.insertAfter(getRegistrationCode());
    }

    private static String getRegistrationCode() {
        String result =
            "if(" + BEAN_REGISTRY_FIELD + "==null){" +
                BEAN_REGISTRY_FIELD + "=new java.util.concurrent.ConcurrentHashMap();" +
            "}"+
            "org.hotswap.agent.plugin.cdi.HaCdiCommons.registerContextClass(this.getScope(),this.getClass());" +
            "if($_!=null && $1 instanceof javax.enterprise.inject.spi.Bean){" +
                "String key=((javax.enterprise.inject.spi.Bean) $1).getBeanClass().getName();" +
                "java.util.Map m=" + BEAN_REGISTRY_FIELD + ".get(key);" +
                "if(m==null) {" +
                    "synchronized(" + BEAN_REGISTRY_FIELD + "){" +
                        "m=" + BEAN_REGISTRY_FIELD + ".get(key);" +
                        "if(m==null) {" +
                            "m=java.util.Collections.synchronizedMap(new java.util.WeakHashMap());" +
                            BEAN_REGISTRY_FIELD + ".put(key,m);" +
                        "}" +
                    "}" +
                "}" +
                "m.put($_, java.lang.Boolean.TRUE);" +
            "}";
        return result;
    }

    /**
     * Return all bean instances.
     *
     * @param bean the bean
     * @return the bean instances
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List<Object> getBeanInstances(Bean<?> bean) {
        List<Object> result = new ArrayList<>();
        Class<? extends Context> contextClass = getContextClass(bean.getScope());
        if (contextClass != null) {
          Map beanRegistry = (Map) getBeanRegistry(contextClass);
          if (beanRegistry != null) {
              Map m = (Map) beanRegistry.get(bean.getBeanClass().getName());
              if (m != null) {
                  result.addAll(m.keySet());
              } else {
                  LOGGER.debug("BeanRegistry is empty for bean class '{}'", bean.getBeanClass().getName());
              }
          } else {
              LOGGER.error("BeanRegistry field not found in context class '{}'", contextClass.getName());
          }
        }
        for (HaCdiExtraContext extraContext: extraContexts.keySet()) {
            List<Object> instances = extraContext.getBeanInstances(bean.getBeanClass());
            if (instances != null) {
                result.addAll(instances);
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Object getBeanRegistry(Class clazz) {
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(BEAN_REGISTRY_FIELD);
                return field.get(null);
            } catch (Exception e) {
                // ignore
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Register context class.
     *
     * @param scope the scope
     * @param contextClass the context class
     */
    public static void registerContextClass(Class<? extends Annotation> scope, Class<? extends Context> contextClass) {

        Map<Class<? extends Annotation>, Class<? extends Context>> currentScopeToContextMap = getCurrentScopeToContextMap();

        if (!currentScopeToContextMap.containsKey(scope)) {
            LOGGER.debug("Registering scope '{}' to scopeToContextMap@{}", scope.getName(), System.identityHashCode(currentScopeToContextMap));
            currentScopeToContextMap.put(scope, contextClass);
        }
    }

    /**
     * Gets the context class for specified scope.
     *
     * @param scope the scope
     * @return the context class
     */
    public static Class<? extends Context> getContextClass(Class<? extends Annotation> scope) {
        return getCurrentScopeToContextMap().get(scope);
    }

    /**
     * Checks if scope is registered
     *
     * @param scope the scope
     * @return true, if is registered scope
     */
    public static boolean isRegisteredScope(Class<? extends Annotation> scope) {
        return getContextClass(scope) != null;
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<? extends Annotation>, Class<? extends Context>> getCurrentScopeToContextMap() {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        if (classLoader != null) {
            try {
                Class<?> clazz = classLoader.loadClass(HaCdiCommons.class.getName());
                if (clazz != HaCdiCommons.class) {
                    return (Map) ReflectionHelper.get(null, clazz, "scopeToContextMap");
                }
            } catch (Exception e) {
                LOGGER.error("getCurrentScopeToContextMap '{}' failed",  e.getMessage());
            }
        }
        return scopeToContextMap;
    }

    /**
     * Register extra context.
     *
     * @param extraContext the extra context
     */
    public static void registerExtraContext(HaCdiExtraContext extraContext) {
        extraContexts.put(extraContext, Boolean.TRUE);
    }

    /**
     * Unregister extra context.
     *
     * @param extraContext the extra context
     */
    public static void unregisterExtraContext(HaCdiExtraContext extraContext) {
        extraContexts.remove(extraContext);
    }

}
