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
            CtField ctField = new CtField(objectClass, "$$ha$prepend", ctClass);
            ctClass.addField(ctField);

            ctClass.addMethod(CtNewMethod.make(
                    "private void $$ha$setupPrepend() {" +
                    "       Class clPaths = Class.forName(\"org.jboss.modules.Paths\", true, this.getClass().getClassLoader());" +
                    "       java.lang.reflect.Method spM  = clPaths.getDeclaredMethod(\"$$ha$setPrepend\", new Class[] {java.lang.Object.class});" +
                    "       spM.invoke(this.paths" + pathsGetter + ", new java.lang.Object[] { $$ha$prepend });"+
                    "}", ctClass)
            );

            // Implementation of HotswapAgentClassLoaderExt.setExtraClassPath(...)
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
                    "           " + ModuleClassLoaderTransformer.class.getName() + ".logSetExtraClassPathException(e);" +
                    "           }" +
                    "       }" +
                    "       this.$$ha$prepend = resLoaderList;" +
                    "       $$ha$setupPrepend();" +
                    "   } catch (java.lang.Exception e) {" +
                    "       " + ModuleClassLoaderTransformer.class.getName() + ".logSetExtraClassPathException(e);" +
                    "   }" +
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


            CtClass ctResLoadClass = classPool.get("org.jboss.modules.ResourceLoaderSpec[]");

            CtMethod methResourceLoaders = ctClass.getDeclaredMethod("setResourceLoaders", new CtClass[] { ctResLoadClass });
            methResourceLoaders.setBody(
                    "{" +
                    "   boolean ret = setResourceLoaders((org.jboss.modules.Paths)this.paths" + pathsGetter + ", $1);" +
                    "   $$ha$setupPrepend();" +
                    "   return ret;" +
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
                    "   if (this.$$ha$prepend != null) {" +
                    "       java.util.Map result = new org.hotswap.agent.plugin.jbossmodules.PrependingMap(this.allPaths, this.$$ha$prepend);" +
                    "       return result;" +
                    "   }" +
                    "   return this.allPaths;"+
                    "}"
            );

            ctClass.addMethod(CtNewMethod.make(
                    "public void $$ha$setPrepend(java.lang.Object prepend) {" +
                    "   this.$$ha$prepend = prepend; " +
                    "}", ctClass)
            );
        } catch (NotFoundException e) {
            LOGGER.warning("Unable to find method \"getAllPaths()\" in org.jboss.modules.Paths.", e);
        }
    }

    public static void logSetExtraClassPathException(Exception e)
    {
        LOGGER.warning("patched ModuleClassLoader.setExtraClassPath(URL[]) exception : ", e.getMessage());
    }

}
