package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into ProxyFactory constructors to register proxy factory into WeldPlugin. If WeldPlugin is not initialized in proxy factory
 * classLoader (ModuleClassLoader) then the proxy factory is not registered - it happens most likely for proxy factories of system beans.
 *
 * @author Vladimir Dvorak
 */
public class ProxyFactoryTransformer {

    /**
     * Patch ProxyFactory class.
     *   - add factory registration into constructor
     *   - changes call classLoader.loadClass(...) in getProxyClass() to ProxyClassLoadingDelegate.loadClass(classLoader, ...)
     *   - changes call ClassFileUtils.toClass() in createProxyClass() to ProxyClassLoadingDelegate.loadClass(...)
     *
     * @param ctClass the ProxyFactory class
     * @param classPool the class pool
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bean.proxy.ProxyFactory")
    public static void patchProxyFactory(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("java.lang.String"),
            classPool.get("java.lang.Class"),
            classPool.get("java.util.Set"),
            classPool.get("javax.enterprise.inject.spi.Bean"),
            classPool.get("boolean")
        };

        CtConstructor declaredConstructor = ctClass.getDeclaredConstructor(constructorParams);

        // TODO : we should find constructor without this() call and put registration only into this one
        declaredConstructor.insertAfter(
            "if (" + PluginManager.class.getName() + ".getInstance().isPluginInitialized(\"" + WeldPlugin.class.getName() + "\", this.classLoader)) {" +
                PluginManagerInvoker.buildCallPluginMethod("this.classLoader", WeldPlugin.class, "registerProxyFactory",
                        "this", "java.lang.Object",
                        "bean", "java.lang.Object",
                        "this.classLoader", "java.lang.ClassLoader"
                        ) +
            "}"
        );

        CtMethod getProxyClassMethod = ctClass.getDeclaredMethod("getProxyClass");
        getProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals(ClassLoader.class.getName()) && m.getMethodName().equals("loadClass"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.weld.command.ProxyClassLoadingDelegate.loadClass(this.classLoader,$1); }");
                    }
                });

        CtMethod createProxyClassMethod = ctClass.getDeclaredMethod("createProxyClass");
        createProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.jboss.weld.util.bytecode.ClassFileUtils") && m.getMethodName().equals("toClass"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.weld.command.ProxyClassLoadingDelegate.toClass($$); }");
                    }
                });
    }

}
