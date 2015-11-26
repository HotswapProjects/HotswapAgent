package org.hotswap.agent.plugin.jbossmodules;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.HotswapAgentClassLoaderExt;

/**
 * JBossModulesPlugin
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "JBossModules",
        description = "JBossModules - Jboss modular class loading implementation. ",
        testedVersions = {"1.4.4"},
        expectedVersions = {"1.x"})
public class JBossModulesPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(JBossModulesPlugin.class);

    // TODO : Skip system packages, it should be in config file
    private static final String SKIP_MODULES_REGEXP = "sun\\.jdk.*|ibm\\.jdk.*|javax\\..*|org\\.jboss\\..*";

    @Init
    ClassLoader moduleClassLoader;

    @OnClassLoadEvent(classNameRegexp = "org.jboss.modules.ModuleLoader")
    public static void transformModule(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        ctClass.getDeclaredMethod("loadModule", new CtClass[]{classPool.get("org.jboss.modules.ModuleIdentifier")}).insertAfter(
                    "if(!identifier.getName().matches(\"" + SKIP_MODULES_REGEXP + "\")) {" +
                        PluginManagerInvoker.buildInitializePlugin(JBossModulesPlugin.class, "$_.getClassLoaderPrivate()") +
                    "}" +
                    "return $_;"
                );

        LOGGER.debug("Class 'org.jboss.modules.Module' patched.");
    }

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

            ctClass.addMethod(CtNewMethod.make(
                    "public void setExtraClassPath(java.net.URL[] extraClassPath) {" +
                    "   try {" +
                    "       java.util.List resLoaderList = new java.util.ArrayList();" +
                    "       for (int i=0; i<extraClassPath.length; i++) {" +
                    "           try {" +
                    "               java.net.URL url = extraClassPath[i];" +
                    "               java.io.File root = new java.io.File(url.getPath());" +
                    "               org.jboss.modules.ResourceLoader resourceLoader = org.jboss.modules.ResourceLoaders.createFileResourceLoader(url.getPath(), root);" +
                    "               resLoaderList.add(resourceLoader);" +
                    "           } catch (java.lang.Exception e) {" +
                    "           " + JBossModulesPlugin.class.getName() + ".logSetExtraClassPathException(e);" +
                    "           }" +
                    "       }" +
                    "       Class clPaths = Class.forName(\"org.jboss.modules.Paths\", true, this.getClass().getClassLoader());" +
                    "       java.lang.reflect.Method spM  = clPaths.getDeclaredMethod(\"setPrepend\", new Class[] {java.lang.Object.class});" +
                    "       spM.invoke(this.paths" + pathsGetter + ", new java.lang.Object[] { resLoaderList });"+
                    "   } catch (java.lang.Exception e) {" +
                    "       " + JBossModulesPlugin.class.getName() + ".logSetExtraClassPathException(e);" +
                    "   }" +
                    "}", ctClass)
            );


            CtMethod methRecalculate = ctClass.getDeclaredMethod("recalculate");
            methRecalculate.setBody(
                    "{" +
                    "   org.jboss.modules.Paths paths = this.paths" + pathsGetter + ";" +
                    "   return setResourceLoaders(paths, (org.jboss.modules.ResourceLoaderSpec[]) paths.getSourceList(NO_RESOURCE_LOADERS));" +
                    "}"
            );

            CtClass ctResLoadClass = classPool.get("org.jboss.modules.ResourceLoaderSpec[]");

            CtMethod methResourceLoaders = ctClass.getDeclaredMethod("setResourceLoaders", new CtClass[] { ctResLoadClass });
            methResourceLoaders.setBody(
                    "{" +
                    "   return setResourceLoaders(paths" + pathsGetter + ", (org.jboss.modules.ResourceLoaderSpec[]) $1);" +
                    "}"
            );

        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find field \"paths\" in org.jboss.modules.ModuleClassLoader.");
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
        CtField ctField = new CtField(objectClass, "__prepend", ctClass);
        ctClass.addField(ctField);

        try {
            CtMethod methGetAllPaths = ctClass.getDeclaredMethod("getAllPaths");
            methGetAllPaths.setBody(
                    "{" +
                    "   if (this.__prepend != null) {" +
                    "       java.util.Map result = new org.hotswap.agent.plugin.jbossmodules.PrependingMap(this.allPaths, this.__prepend);" +
                    "       return result;" +
                    "   }" +
                    "   return this.allPaths;"+
                    "}"
            );

            ctClass.addMethod(CtNewMethod.make(
                    "public void setPrepend(java.lang.Object prepend) {" +
                    "   this.__prepend = prepend; " +
                    "}", ctClass)
            );
        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find methos \"getAllPaths()\" in org.jboss.modules.Paths.");
        }
    }

    public static void logSetExtraClassPathException(Exception e)
    {
        LOGGER.warning("patched ModuleClassLoader.setExtraClassPath(URL[]) exception : ", e.getMessage());
    }

}
