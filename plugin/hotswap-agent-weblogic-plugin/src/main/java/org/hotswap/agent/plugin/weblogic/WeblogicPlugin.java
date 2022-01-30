// ModuleClassLoaderTransformerKt.java
package org.hotswap.agent.plugin.weblogic;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.classloader.HotswapAgentClassLoaderExt;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;

@Plugin(name = "Weblogic",
        description = "Weblogic plugin for dcevm",
        testedVersions = {"12.2.1.4"},
        expectedVersions = {"12c"}
)
public final class WeblogicPlugin {

    @Init
    ClassLoader moduleClassLoader;

    static protected AgentLogger LOGGER = AgentLogger.getLogger(WeblogicPlugin.class);

    @OnClassLoadEvent(classNameRegexp = "weblogic.utils.classloaders.ChangeAwareClassLoader")
    public static void transformChangeAwareClassLoader(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.info("transformChangeAwareClassLoader: {}", ctClass.getSimpleName());

        String src = WeblogicPlugin.class.getName() + ".logMessage(\"ChangeAwareClassLoaderConstructor -> \" + $1.toString());";

        ctClass.getDeclaredConstructor(new CtClass[] { classPool.get("weblogic.utils.classloaders.ClassFinder"), CtClass.booleanType, classPool.get("java.lang.ClassLoader") })
               .insertBefore(src);

    }

    @OnClassLoadEvent(classNameRegexp = "weblogic.utils.classloaders.MultiClassFinder")
    public static void transformMultiClassFinder(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.info("MultiClassFinder: {}", ctClass.getSimpleName());

        String srcAddFinder = WeblogicPlugin.class.getName() + ".logMessage(\"MultiClassFinder#addFinder -> \" + $1.toString());";

        ctClass.getDeclaredMethod("addFinder", new CtClass[] { classPool.get("weblogic.utils.classloaders.ClassFinder") }).insertBefore(srcAddFinder);

        String srcAddFinderFirst = WeblogicPlugin.class.getName() + ".logMessage(\"MultiClassFinder#addFinderFirst -> \" + $1.toString());";

        ctClass.getDeclaredMethod("addFinderFirst", new CtClass[] { classPool.get("weblogic.utils.classloaders.ClassFinder") }).insertBefore(srcAddFinderFirst);
    }

    @OnClassLoadEvent(classNameRegexp = "weblogic.utils.classloaders.CompositeWebAppFinder")
    public static void transformCompositeWebAppFinder(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.info("CompositeWebAppFinder: {}", ctClass.getSimpleName());

        String src = WeblogicPlugin.class.getName() + ".logMessage(\"CompositeWebAppFinder#addLibraryFinder -> \" + $1.toString());";

        ctClass.getDeclaredMethod("addLibraryFinder", new CtClass[] { classPool.get("weblogic.utils.classloaders.ClassFinder") }).insertBefore(src);
    }

    @OnClassLoadEvent(classNameRegexp = "weblogic.utils.classloaders.GenericClassLoader")
    public static void transformGenericClassLoader(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.info("transformGenericClassLoader: {}", ctClass.getSimpleName());

        CtClass ctHaClassLoader = classPool.get(HotswapAgentClassLoaderExt.class.getName());
        ctClass.addInterface(ctHaClassLoader);

        // Implementation of HotswapAgentClassLoaderExt.setExtraClassPath(...)
        //@formatter:off
        ctClass.addMethod(CtNewMethod.make(
                "public void $$ha$setExtraClassPath(java.net.URL[] extraClassPath) {" + 
                        WeblogicPlugin.class.getName() + ".logMessage(\"setExtraClassPath in=\" + extraClassPath[0].toString());" +
                        "try {" +
                            "weblogic.utils.classloaders.MultiClassFinder multiClassFinder = new weblogic.utils.classloaders.MultiClassFinder();" +
                            "for (int i=0; i<extraClassPath.length; i++) {" +
                                "try {" +
                                    "java.net.URL url = extraClassPath[i];" +
                                    "java.io.File root = new java.io.File(url.getPath());" +
                                    "weblogic.utils.classloaders.IndexedDirectoryClassFinder indexedDirectoryClassFinder = new weblogic.utils.classloaders.IndexedDirectoryClassFinder(root);" +
                                    "multiClassFinder.addFinder(indexedDirectoryClassFinder);" +
                                "} catch (java.lang.Exception e) {" +
                                    WeblogicPlugin.class.getName() + ".logException(e);" +
                                "}" +
                            "}" +
                            "this.addClassFinderFirst(multiClassFinder);" +
                            WeblogicPlugin.class.getName() + ".logMessage(\"setExtraClassPath result=\" + this.getClassPath());" +
                        "} catch (java.lang.Exception e) {" +
                            WeblogicPlugin.class.getName() + ".logException(e);" +
                        "}" +
                    "}", ctClass)
        );
        //@formatter:on

        ctClass.addMethod(
                CtNewMethod.make(
                        "public void $$ha$setWatchResourceLoader(" + WatchResourcesClassLoader.class.getName() + " watchResourceLoader) { " +
                        WeblogicPlugin.class.getName() + ".logMessage(\"WatchResourcesClassLoader -> \" + watchResourceLoader.toString());" +
                        "}",
                        ctClass
                ));
    }

    public static void logMessage(String str){
        LOGGER.info("logmessage: {}", str);
    }

    public static void logException(Exception e){
        LOGGER.error("logException: {}", e);
    }
}
