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
