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
package org.hotswap.agent.plugin.jbossmodules;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.classloader.HotswapAgentClassLoaderExt;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;

/**
 * ModuleClassLoaderTransformer
 *
 * @author Vladimir Dvorak
 */
public class ModuleClassLoaderTransformer {

    protected static AgentLogger LOGGER = AgentLogger.getLogger(ModuleClassLoaderTransformer.class);
    /**
     *
     * @param ctClass the ct class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.modules.ModuleClassLoader")
    public static void patchModuleClassLoader(ClassPool classPool, CtClass ctClass) throws CannotCompileException {
        try {
            CtField pathField = ctClass.getDeclaredField("paths");
            CtClass pathsType = pathField.getType();
            String pathsGetter = "";

            CtClass ctHaClassLoader = classPool.get(HotswapAgentClassLoaderExt.class.getName());
            ctClass.addInterface(ctHaClassLoader);

            if ("java.util.concurrent.atomic.AtomicReference".equals(pathsType.getName())) {
                // version>=1.5
                pathsGetter = ".get()";
            }

            CtClass objectClass = classPool.get(Object.class.getName());
            CtField ctPrependField = new CtField(objectClass, "$$ha$prepend", ctClass);
            ctClass.addField(ctPrependField);

            ctClass.addMethod(CtNewMethod.make(
                    "private void $$ha$setupPrepend() {" +
                            "Class clPaths = Class.forName(\"org.jboss.modules.Paths\", true, this.getClass().getClassLoader());" +
                            "java.lang.reflect.Method spM  = clPaths.getDeclaredMethod(\"$$ha$setPrepend\", new Class[] {java.lang.Object.class});" +
                            "spM.invoke(this.paths" + pathsGetter + ", new java.lang.Object[] { $$ha$prepend });"+
                    "}", ctClass)
            );

            // Implementation of HotswapAgentClassLoaderExt.setExtraClassPath(...)
            ctClass.addMethod(CtNewMethod.make(
                    "public void $$ha$setExtraClassPath(java.net.URL[] extraClassPath) {" +
                        "try {" +
                            "java.util.List resLoaderList = new java.util.ArrayList();" +
                            "for (int i=0; i<extraClassPath.length; i++) {" +
                                "try {" +
                                    "java.net.URL url = extraClassPath[i];" +
                                    "java.io.File root = new java.io.File(url.getPath());" +
                                    "org.jboss.modules.ResourceLoader resourceLoader = org.jboss.modules.ResourceLoaders.createFileResourceLoader(url.getPath(), root);" +
                                    "resLoaderList.add(resourceLoader);" +
                                "} catch (java.lang.Exception e) {" +
                                    ModuleClassLoaderTransformer.class.getName() + ".logSetExtraClassPathException(e);" +
                                "}" +
                            "}" +
                            "this.$$ha$prepend = resLoaderList;" +
                            "$$ha$setupPrepend();" +
                        "} catch (java.lang.Exception e) {" +
                            ModuleClassLoaderTransformer.class.getName() + ".logSetExtraClassPathException(e);" +
                        "}" +
                    "}", ctClass)
            );

            CtClass watchResClassLoaderClass = classPool.get(WatchResourcesClassLoader.class.getName());
            CtField watchResClassLoaderField = new CtField(watchResClassLoaderClass, "$$ha$watchResourceLoader", ctClass);
            ctClass.addField(watchResClassLoaderField);

            ctClass.addMethod(CtNewMethod.make(
                    "public void $$ha$setWatchResourceLoader(" + WatchResourcesClassLoader.class.getName() + " watchResourceLoader) {" +
                        "this.$$ha$watchResourceLoader = watchResourceLoader;" +
                    "}", ctClass)
            );

            CtMethod methRecalculate = ctClass.getDeclaredMethod("recalculate");
            methRecalculate.setName("_recalculate");

            ctClass.addMethod(CtNewMethod.make(
                    "boolean recalculate() {" +
                    "   boolean ret = _recalculate();" +
                    "   $$ha$setupPrepend();" +
                    "   return ret;" +
                    "}",
                    ctClass
            ));


            CtClass resourceLoaderSpecClass = classPool.get("org.jboss.modules.ResourceLoaderSpec[]");
            ctClass.getDeclaredMethod("setResourceLoaders", new CtClass[] { resourceLoaderSpecClass }).setBody(
                    "{" +
                        "boolean ret = setResourceLoaders((org.jboss.modules.Paths)this.paths" + pathsGetter + ", $1);" +
                        "$$ha$setupPrepend();" +
                        "return ret;" +
                    "}"
            );

            // patch: URL findResource(final String name, final boolean exportsOnly)
            ctClass.getDeclaredMethod("findResource", new CtClass[] { classPool.get(String.class.getName()), CtClass.booleanType }).insertBefore(
                    "if (this.$$ha$watchResourceLoader != null){" +
                        "java.net.URL resource = this.$$ha$watchResourceLoader.getResource($1);" +
                        "if(resource != null)" +
                            "return resource;" +
                    "}"
            );

            // patch: Enumeration<URL> findResources(final String name, final boolean exportsOnly)
            ctClass.getDeclaredMethod("findResources", new CtClass[] { classPool.get(String.class.getName()), CtClass.booleanType }).insertBefore(
                    "if (this.$$ha$watchResourceLoader != null){" +
                        "try {" +
                            "java.util.Enumeration resources = this.$$ha$watchResourceLoader.getResources($1);" +
                            "if (resources != null && resources.hasMoreElements())" +
                                "return resources;" +
                        "} catch (java.io.IOException e) {}" +
                    "}"
            );

        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find field \"paths\" in org.jboss.modules.ModuleClassLoader.", e);
        }
    }

    /**
     *
     * @param classPool the class pool
     * @param ctClass the ct class
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.modules.Paths")
    public static void patchModulesPaths(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtClass objectClass = classPool.get(Object.class.getName());
        CtField ctField = new CtField(objectClass, "$$ha$prepend", ctClass);
        ctClass.addField(ctField);

        try {
            CtMethod methGetAllPaths = ctClass.getDeclaredMethod("getAllPaths");
            methGetAllPaths.setBody(
                    "{" +
                        "if (this.$$ha$prepend != null) {" +
                            "java.util.Map result = new org.hotswap.agent.plugin.jbossmodules.PrependingMap(this.allPaths, this.$$ha$prepend);" +
                            "return result;" +
                        "}" +
                        "return this.allPaths;"+
                    "}"
            );

            ctClass.addMethod(CtNewMethod.make(
                    "public void $$ha$setPrepend(java.lang.Object prepend) {" +
                        "this.$$ha$prepend = prepend; " +
                    "}", ctClass)
            );
        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find method \"getAllPaths()\" in org.jboss.modules.Paths.", e);
        }
    }

    public static void logSetExtraClassPathException(Exception e)
    {
        LOGGER.warning("patched ModuleClassLoader.$$ha$setExtraClassPath(URL[]) exception : ", e.getMessage());
    }

}
