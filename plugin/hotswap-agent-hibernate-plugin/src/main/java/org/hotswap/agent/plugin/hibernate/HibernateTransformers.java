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
package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.javassist.bytecode.AccessFlag;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate.proxy.SessionFactoryProxy;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for Hibernate plugin.
 */
public class HibernateTransformers {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernateTransformers.class);

    /**
     * Override HibernatePersistence.createContainerEntityManagerFactory() to return EntityManagerFactory proxy object.
     * {@link org.hotswap.agent.plugin.hibernate.proxy.EntityManagerFactoryProxy} holds reference to all proxied factories
     * and on refresh command replaces internal factory with fresh instance.
     * <p/>
     * Two variants covered - createContainerEntityManagerFactory and createEntityManagerFactory.
     * <p/>
     * After the entity manager factory and it's proxy are instantiated, plugin init method is invoked.
     */
    @OnClassLoadEvent(classNameRegexp = "(org.hibernate.ejb.HibernatePersistence)|(org.hibernate.jpa.HibernatePersistenceProvider)|(org.springframework.orm.jpa.vendor.SpringHibernateJpaPersistenceProvider)|(org.springframework.orm.jpa.vendor.SpringHibernateEjbPersistenceProvider)")
    public static void proxyHibernatePersistence(CtClass clazz) throws Exception {
        LOGGER.debug("Override org.hibernate.ejb.HibernatePersistence#createContainerEntityManagerFactory and createEntityManagerFactory to create a EntityManagerFactoryProxy proxy.");

        CtMethod oldMethod = clazz.getDeclaredMethod("createContainerEntityManagerFactory");
        oldMethod.setName("_createContainerEntityManagerFactory" + clazz.getSimpleName());
        CtMethod newMethod = CtNewMethod.make(
                "public javax.persistence.EntityManagerFactory createContainerEntityManagerFactory(" +
                        "           javax.persistence.spi.PersistenceUnitInfo info, java.util.Map properties) {" +
                        "  properties.put(\"PERSISTENCE_CLASS_NAME\", \"" + clazz.getName() + "\");" +
                        "  return " + HibernatePersistenceHelper.class.getName() + ".createContainerEntityManagerFactoryProxy(" +
                        "      this, info, properties, _createContainerEntityManagerFactory" + clazz.getSimpleName() + "(info, properties)); " +
                        "}", clazz);
        clazz.addMethod(newMethod);

        try {
            oldMethod = clazz.getDeclaredMethod("createEntityManagerFactory");
            oldMethod.setName("_createEntityManagerFactory" + clazz.getSimpleName());

            newMethod = CtNewMethod.make(
                    "public javax.persistence.EntityManagerFactory createEntityManagerFactory(" +
                            "           String persistenceUnitName, java.util.Map properties) {" +
                            "  return " + HibernatePersistenceHelper.class.getName() + ".createEntityManagerFactoryProxy(" +
                            "      this, persistenceUnitName, properties, _createEntityManagerFactory" + clazz.getSimpleName() + "(persistenceUnitName, properties)); " +
                            "}", clazz);
            clazz.addMethod(newMethod);
        } catch (NotFoundException e) {
            LOGGER.trace("Method createEntityManagerFactory not found on " + clazz.getName() + ". Is Ok for Spring implementation...", e);
        }
    }

    /**
     * Remove final flag from SessionFactoryImpl - we need to create a proxy on session factory and cannot
     * use SessionFactory interface, because hibernate makes type cast to impl.
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.internal.SessionFactoryImpl")
    public static void removeSessionFactoryImplFinalFlag(CtClass clazz) throws Exception {
        clazz.getClassFile().setAccessFlags(AccessFlag.PUBLIC);
    }

    @OnClassLoadEvent(classNameRegexp = "org.hibernate.cfg.Configuration")
    public static void proxySessionFactory(ClassLoader classLoader, ClassPool classPool, CtClass clazz) throws Exception {
        // proceed only if EJB not available by the classloader
        if (checkHibernateEjb(classLoader))
            return;

        LOGGER.debug("Override org.hibernate.cfg.Configuration#buildSessionFactory to create a SessionFactoryProxy proxy.");

        CtClass serviceRegistryClass = classPool.makeClass("org.hibernate.service.ServiceRegistry");
        CtMethod oldMethod = clazz.getDeclaredMethod("buildSessionFactory", new CtClass[]{serviceRegistryClass});
        oldMethod.setName("_buildSessionFactory");

        CtMethod newMethod = CtNewMethod.make(
                "public org.hibernate.SessionFactory buildSessionFactory(org.hibernate.service.ServiceRegistry serviceRegistry) throws org.hibernate.HibernateException {" +
                        "  return " + SessionFactoryProxy.class.getName() + ".getWrapper(this)" +
                        "       .proxy(_buildSessionFactory(serviceRegistry), serviceRegistry); " +
                        "}", clazz);
        clazz.addMethod(newMethod);
    }

    // check if plain Hibernate or EJB mode.
    private static boolean checkHibernateEjb(ClassLoader classLoader) {
        try {
            classLoader.loadClass("org.hibernate.ejb.HibernatePersistence");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.hibernate.validator.internal.metadata.BeanMetaDataManager")
    public static void beanMetaDataManagerRegisterVariable(CtClass ctClass) throws CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(HibernatePlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(HibernatePlugin.class, "registerBeanMetaDataManager",
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

    @OnClassLoadEvent(classNameRegexp = "org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider")
    public static void annotationMetaDataProviderRegisterVariable(CtClass ctClass) throws CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(HibernatePlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(HibernatePlugin.class, "registerAnnotationMetaDataProvider",
                "this", "java.lang.Object"));
        src.append("}");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }
        try {
            ctClass.getDeclaredField("configuredBeans");
            ctClass.addMethod(CtNewMethod.make(
                    "public void __resetCache() {"
                  + "   this.configuredBeans.clear(); " + "}",
                    ctClass));
        } catch (org.hotswap.agent.javassist.NotFoundException e) {
            // Ignore, newer Hibernate versions have no cache
            ctClass.addMethod(CtNewMethod.make(
                    "public void __resetCache() {"
                  + "}",
                    ctClass));
        }
        LOGGER.debug("org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider - added method __resetCache().");
    }


}
