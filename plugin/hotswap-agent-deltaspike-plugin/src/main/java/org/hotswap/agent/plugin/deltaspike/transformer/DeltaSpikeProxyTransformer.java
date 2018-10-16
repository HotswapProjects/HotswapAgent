package org.hotswap.agent.plugin.deltaspike.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Register proxy factory to DeltaSpikePlugin, patch AsmProxyClassGenerator
 *
 * @author Vladimir Dvorak
 */
public class DeltaSpikeProxyTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaSpikeProxyTransformer.class);

    /**
     * Delegates ClassUtils.tryToLoadClassForName to org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate::tryToLoadClassForName
     *
     * @param ctClass
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.api.DeltaSpikeProxyFactory")
    public static void patchDeltaSpikeProxyFactory(CtClass ctClass) throws NotFoundException, CannotCompileException {
        // Deltaspike 1.5
        instrumentTryToLoadClassForName(ctClass, "getProxyClass");
        instrumentTryToLoadClassForName(ctClass, "createProxyClass");
        // Deltaspike 1.7
        instrumentTryToLoadClassForName(ctClass, "resolveAlreadyDefinedProxyClass");
    }

    private static void instrumentTryToLoadClassForName(CtClass ctClass, String methodName) throws CannotCompileException {
        try {
            CtMethod getProxyClassMethod = ctClass.getDeclaredMethod(methodName);
            getProxyClassMethod.instrument(
                    new ExprEditor() {
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals("org.apache.deltaspike.core.util.ClassUtils") && m.getMethodName().equals("tryToLoadClassForName"))
                                m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate.tryToLoadClassForName($$); }");
                        }
                    });
        } catch (NotFoundException e) {
            LOGGER.debug("Method '{}' not found in '{}'.", methodName, ctClass.getName());
        }
    }

    /**
     * Delegates loadClass to org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate::loadClass
     *
     * @param ctClass
     * @throws NotFoundException the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.proxy.impl.AsmProxyClassGenerator")
    public static void patchAsmProxyClassGenerator(CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtMethod generateProxyClassMethod = ctClass.getDeclaredMethod("generateProxyClass");
        generateProxyClassMethod.instrument(
                new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getClassName().equals("org.apache.deltaspike.proxy.impl.AsmProxyClassGenerator") && m.getMethodName().equals("loadClass"))
                            m.replace("{ $_ = org.hotswap.agent.plugin.deltaspike.proxy.ProxyClassLoadingDelegate.loadClass($$); }");
                    }
                });
    }

}
