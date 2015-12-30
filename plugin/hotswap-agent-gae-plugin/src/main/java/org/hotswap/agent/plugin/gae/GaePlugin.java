package org.hotswap.agent.plugin.gae;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
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
        String src = "{" +
                    PluginManagerInvoker.buildInitializePlugin(GaePlugin.class, "$_.getClass().getClassLoader()") +
                "}" +
                "return $_;";
        getCreateDevAppServerMethod.insertAfter(src);
    }

    @OnClassLoadEvent(classNameRegexp = "com.google.appengine.tools.development.DevAppServerClassLoader")
    public static void patchDevAppServerClassLoader(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod getNewClassLoaderMethod = ctClass.getDeclaredMethod("newClassLoader");
        getNewClassLoaderMethod.instrument(
                new ExprEditor() {
                    boolean finish = false;
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (!finish && m.getClassName().equals("java.util.List") && m.getMethodName().equals("addAll")) {
                            finish = true;
                            m.replace(
                            "{" +
                            "   java.security.ProtectionDomain _domain = " + PluginManager.class.getName() + ".class.getProtectionDomain();" +
                            "   if (_domain != null) { " +
                            "       java.security.CodeSource _source = _domain.getCodeSource(); " +
                            "       if (_source != null) { " +
                            "           java.net.URL _location = _source.getLocation();" +
                            "           if (_location != null) {" +
                            "               libs.add(_location);" +
                            "           }" +
                            "       }" +
                            "   }" +
                            "   $_ = $proceed($$);" +
                            "}"
                            );
                        }
                    }
                });
    }

}
