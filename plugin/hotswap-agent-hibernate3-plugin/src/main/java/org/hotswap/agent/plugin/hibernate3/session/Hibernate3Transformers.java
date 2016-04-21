/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hotswap.agent.plugin.hibernate3.session;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.session.proxy.SessionFactoryProxy;
import org.hotswap.agent.plugin.hibernate3.session.util.ProxyUtil;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for Hibernate Session factory plugin.
 * 
 * @author alpapad@gmail.com
 */
public class Hibernate3Transformers {

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3Transformers.class);

    /**
     * Ensure org.hibernate.impl.SessionFactoryImpl is Proxyable
     *
     * @param clazz
     *            the clazz
     * @param classPool
     *            the class pool
     * @param classLoader
     *            the class loader
     * @throws Exception
     *             the exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.impl.SessionFactoryImpl")
    public static void removeSessionFactoryImplFinalFlag(CtClass clazz, ClassPool classPool, ClassLoader classLoader) throws Exception {
        ProxyUtil.ensureProxyable(clazz);
        LOGGER.info("Override org.hibernate.impl.SessionFactoryImpl {}", classLoader);
    }

    /**
     * Patch org.hibernate.cfg.Configuration with ReInitializable features. When
     * java8+ is supprted, then make methods default in ReInitializable
     *
     * @param classLoader
     *            the class loader
     * @param classPool
     *            the class pool
     * @param clazz
     *            the clazz
     * @throws Exception
     *             the exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.cfg.Configuration")
    public static void proxySessionFactory(ClassLoader classLoader, ClassPool classPool, CtClass clazz) throws Exception {

        LOGGER.debug("Adding interface o.h.a.p.h.s.p.ReInitializable to org.hibernate.cfg.Configuration.");

        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.hibernate3.session.proxy.ReInitializable"));

        CtField field = CtField.make("private org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig $$override  = new org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig();", clazz);

        clazz.addField(field);

        LOGGER.debug("Patching org.hibernate.cfg.Configuration#buildSessionFactory to create a SessionFactoryProxy proxy.");

        CtMethod oldMethod = clazz.getDeclaredMethod("buildSessionFactory");
        oldMethod.setName("_buildSessionFactory");

        CtMethod newMethod = CtNewMethod.make(//
                "public org.hibernate.SessionFactory buildSessionFactory() throws org.hibernate.HibernateException {" + //
                        "  return " + SessionFactoryProxy.class.getName() + //
                        "       .getWrapper(this)" + //
                        "       .proxy(_buildSessionFactory()); " + //
                        "}",
                clazz);
        clazz.addMethod(newMethod);

        LOGGER.debug("Adding org.hibernate.cfg.Configuration.reInitialize() method");
        CtMethod reInitMethod = CtNewMethod.make(//
                "public void reInitialize(){" + //
                        "  this.settingsFactory = new org.hibernate.cfg.SettingsFactory();" + //
                        "  this.reset();" + //
                        "}",
                clazz);

        clazz.addMethod(reInitMethod);

        LOGGER.debug("Adding org.hibernate.cfg.Configuration.getOverrideConfig() method");
        CtMethod internalPropsMethod = CtNewMethod.make(//
                "public org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig getOverrideConfig(){" + //
                        "  return $$override;" + //
                        "}",
                clazz);

        clazz.addMethod(internalPropsMethod);

        CtConstructor con = clazz.getDeclaredConstructor(new CtClass[] {});

        LOGGER.debug("Patching org.hibernate.cfg.Configuration.<init>");
        con.insertAfter(//
                "java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();"//
                        + PluginManagerInvoker.buildInitializePlugin(Hibernate3Plugin.class, "$$cl")//
                        + "java.lang.String $$version = org.hibernate.Version.getVersionString();" //
                        + PluginManagerInvoker.buildCallPluginMethod("$$cl", Hibernate3Plugin.class, "setVersion", "$$version", "java.lang.String")//
        );

        ProxyUtil.addMethod(classLoader, classPool, clazz, "void", "hotSwap", null);
        ProxyUtil.addMethod(classLoader, classPool, clazz, "org.hibernate.cfg.Configuration", "setProperty", new String[] { "java.lang.String", "java.lang.String" });

        ProxyUtil.addMethod(classLoader, classPool, clazz, "org.hibernate.cfg.Configuration", "configure", new String[] { "java.lang.String" });
        ProxyUtil.addMethod(classLoader, classPool, clazz, "org.hibernate.cfg.Configuration", "configure", new String[] { "java.net.URL" });
        ProxyUtil.addMethod(classLoader, classPool, clazz, "org.hibernate.cfg.Configuration", "configure", new String[] { "java.io.File" });
        ProxyUtil.addMethod(classLoader, classPool, clazz, "org.hibernate.cfg.Configuration", "configure", new String[] { "org.w3c.dom.Document" });
        ProxyUtil.addMethod(classLoader, classPool, clazz, "org.hibernate.cfg.Configuration", "configure", null);

        LOGGER.info("Hibernate3Plugin, patched org.hibernate.cfg.Configuration");
    }

    /**
     * If org.hibernate.ejb.HibernatePersistence is loaded then we live in a JPA
     * environment. Disable the Hibernate3Plugin reload command
     *
     * @param clazz
     *            the clazz
     * @throws Exception
     *             the exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.ejb.HibernatePersistence")
    public static void proxyHibernatePersistence(CtClass clazz) throws Exception {
        CtConstructor con = clazz.getDeclaredConstructor(new CtClass[] {});

        LOGGER.debug("org.hibernate.ejb.HibernatePersistence.<init>");
        con.insertAfter(//
                "java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();"//
                        + PluginManagerInvoker.buildInitializePlugin(Hibernate3Plugin.class, "$$cl")//
                        + PluginManagerInvoker.buildCallPluginMethod("$$cl", Hibernate3Plugin.class, "disable")//
        );
    }
}
