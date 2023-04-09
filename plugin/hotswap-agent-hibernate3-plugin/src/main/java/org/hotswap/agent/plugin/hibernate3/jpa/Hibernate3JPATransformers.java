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
package org.hotswap.agent.plugin.hibernate3.jpa;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for Hibernate plugin.
 */
public class Hibernate3JPATransformers {

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3JPATransformers.class);

    /**
     * Override HibernatePersistence.createContainerEntityManagerFactory() to return EntityManagerFactory proxy object.
     * {@link org.hotswap.agent.plugin.hibernate3.jpa.proxy.EntityManagerFactoryProxy} holds reference to all proxied factories
     * and on refresh command replaces internal factory with fresh instance.
     * <p/>
     * Two variants covered - createContainerEntityManagerFactory and createEntityManagerFactory.
     * <p/>
     * After the entity manager factory and it's proxy are instantiated, plugin init method is invoked.
     *
     * @param clazz the clazz
     * @throws Exception the exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.ejb.HibernatePersistence")
    public static void proxyHibernatePersistence(CtClass clazz) throws Exception {
        LOGGER.debug("Override org.hibernate.ejb.HibernatePersistence#createContainerEntityManagerFactory and createEntityManagerFactory to create a EntityManagerFactoryProxy proxy.");

        CtMethod oldMethod = clazz.getDeclaredMethod("createContainerEntityManagerFactory");
        oldMethod.setName("_createContainerEntityManagerFactory" + clazz.getSimpleName());

        CtMethod newMethod = CtNewMethod.make(
                "public javax.persistence.EntityManagerFactory createContainerEntityManagerFactory(javax.persistence.spi.PersistenceUnitInfo info, java.util.Map properties) {" +
                        "  return " + Hibernate3JPAHelper.class.getName() + ".createContainerEntityManagerFactoryProxy(" +
                        "      info, properties, _createContainerEntityManagerFactory" + clazz.getSimpleName() + "(info, properties)); " +
                        "}", clazz);
        clazz.addMethod(newMethod);

        oldMethod = clazz.getDeclaredMethod("createEntityManagerFactory");
        oldMethod.setName("_createEntityManagerFactory" + clazz.getSimpleName());

        newMethod = CtNewMethod.make(
                "public javax.persistence.EntityManagerFactory createEntityManagerFactory(String persistenceUnitName, java.util.Map properties) {" +
                        "  return " + Hibernate3JPAHelper.class.getName() + ".createEntityManagerFactoryProxy(" +
                        "      persistenceUnitName, properties, _createEntityManagerFactory" + clazz.getSimpleName() + "(persistenceUnitName, properties)); " +
                        "}", clazz);
        clazz.addMethod(newMethod);
    }

    /**
     * Bean meta data manager register variable.
     *
     * @param ctClass the ct class
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.validator.internal.metadata.BeanMetaDataManager")
    public static void beanMetaDataManagerRegisterVariable(CtClass ctClass) throws CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(Hibernate3JPAPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(Hibernate3JPAPlugin.class, "registerBeanMetaDataManager",
                "this", "java.lang.Object"));
        src.append("}");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        ctClass.addMethod(CtNewMethod.make("public void __resetCache() {" +
                "   this.beanMetaDataCache.clear(); " +
                "}", ctClass));

        LOGGER.debug("org.hibernate.validator.internal.metadata.BeanMetaDataManager - added method __resetCache().");
    }

    /**
     * Annotation meta data provider register variable.
     *
     * @param ctClass the ct class
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider")
    public static void annotationMetaDataProviderRegisterVariable(CtClass ctClass) throws CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(Hibernate3JPAPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(Hibernate3JPAPlugin.class, "registerAnnotationMetaDataProvider",
                "this", "java.lang.Object"));
        src.append("}");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        ctClass.addMethod(CtNewMethod.make("public void __resetCache() {" +
                "   this.configuredBeans.clear(); " +
                "}", ctClass));

        LOGGER.debug("org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider - added method __resetCache().");
    }


}
