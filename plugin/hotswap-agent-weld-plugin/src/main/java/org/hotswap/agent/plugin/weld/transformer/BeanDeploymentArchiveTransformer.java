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
package org.hotswap.agent.plugin.weld.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.weld.WeldPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into WeldBeanDeploymentArchive or BeanDeploymentArchiveImpl(WildFly) constructors to initialize WeldPlugin
 *
 * @author Vladimir Dvorak
 */
public class BeanDeploymentArchiveTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeploymentArchiveTransformer.class);

    /**
     * Basic WeldBeanDeploymentArchive transformation.
     *
     * @param classPool the class pool
     * @param clazz     the clazz
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive")
    public static void transform(ClassPool classPool, CtClass clazz) throws NotFoundException, CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "init"));
        src.append("org.hotswap.agent.plugin.weld.command.BeanClassRefreshAgent.registerArchive(getClass().getClassLoader(), this, null);");
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class '{}' patched with BDA registration.", clazz.getName());
    }

    /**
     * JbossAS (Wildfly) BeanDeploymentArchiveImpl transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl")
    public static void transformJbossBda(ClassPool classPool, CtClass clazz) throws NotFoundException, CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        StringBuilder src = new StringBuilder("{");
        src.append("if (beansXml!=null && beanArchiveType!=null && (\"EXPLICIT\".equals(beanArchiveType.toString()) || \"IMPLICIT\".equals(beanArchiveType.toString()))){");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class, "module.getClassLoader()"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("module.getClassLoader()", WeldPlugin.class, "initInJBossAS"));
        src.append("    Class agC = Class.forName(\"org.hotswap.agent.plugin.weld.command.BeanClassRefreshAgent\", true, module.getClassLoader());");
        src.append("    java.lang.reflect.Method agM  = agC.getDeclaredMethod(\"registerArchive\", new Class[] {java.lang.ClassLoader.class, org.jboss.weld.bootstrap.spi.BeanDeploymentArchive.class, java.lang.String.class});");
        src.append("    agM.invoke(null, new Object[] { module.getClassLoader(),this, beanArchiveType.toString()});");
        src.append("}}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class 'org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl' patched with BDA registration.");
    }

    /**
     * GlassFish BeanDeploymentArchiveImpl transformation.
     *
     * @param classPool the class pool
     * @param clazz     the clazz
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.weld.BeanDeploymentArchiveImpl")
    public static void transformGlassFishBda(ClassPool classPool, CtClass clazz) throws NotFoundException, CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class, "this.moduleClassLoaderForBDA"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("this.moduleClassLoaderForBDA", WeldPlugin.class, "initInGlassFish"));
        src.append("    Class agC = Class.forName(\"org.hotswap.agent.plugin.weld.command.BeanClassRefreshAgent\", true, this.moduleClassLoaderForBDA);");
        src.append("    java.lang.reflect.Method agM  = agC.getDeclaredMethod(\"registerArchive\", new Class[] {java.lang.ClassLoader.class, org.jboss.weld.bootstrap.spi.BeanDeploymentArchive.class, java.lang.String.class});");
        src.append("    agM.invoke(null, new Object[] { this.moduleClassLoaderForBDA, this, null});");
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class 'org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl' patched with BDA registration.");
    }
}
