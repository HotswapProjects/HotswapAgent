package org.hotswap.agent.plugin.gae;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Gae - Google App Engine
 *
 *  @author Vladimir Dvorak
 */
@Plugin(name = "Gae",
        description = "Google App Engine",
        testedVersions = {""},
        expectedVersions = {""}
        )
public class GaePlugin {

    @OnClassLoadEvent(classNameRegexp = "com.google.appengine.tools.development.DevAppServerFactory")
    public static void patchDevAppServerFactory(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod getCreateDevAppServerMethod = ctClass.getDeclaredMethod("doCreateDevAppServer");
        getCreateDevAppServerMethod.insertAfter(
                "{" +
                        PluginManagerInvoker.buildInitializePlugin(GaePlugin.class, "$_.getClass().getClassLoader()") +
                "}" +
                "return $_;"
        );
    }

    @OnClassLoadEvent(classNameRegexp = "com.google.appengine.tools.development.DevAppServerClassLoader")
    public static void patchDevAppServerClassLoader(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod getLoadClass = ctClass.getDeclaredMethod("loadClass");
        getLoadClass.insertBefore(
                "if (name.startsWith(\"org.hotswap.agent.\")) {" +
                "   java.lang.Class c = delegate.loadClass(name);" +
                "   if (resolve)" +
                "       resolveClass(c); " +
                "   return c;" +
                "}"
                );
    }

}
