package org.hotswap.agent.plugin.owb;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * WebBeanContextTransformer
 *
 * @author Vladimir Dvorak
 */
public class WebBeanContextTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WebBeanContextTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.apache.webbeans.config.WebBeansContext")
    public static void transform(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("java.util.Map"),
            classPool.get("org.apache.webbeans.config.OpenWebBeansConfiguration")
        };

        CtConstructor declaredConstructor = ctClass.getDeclaredConstructor(constructorParams);

        declaredConstructor.insertBefore(
                "$2.setProperty(" +
                        "\"org.apache.webbeans.proxy.mapping.javax.enterprise.context.ApplicationScoped\"," +
                        "\"org.apache.webbeans.intercept.NormalScopedBeanInterceptorHandler\"" +
                ");"
        );

        LOGGER.debug("Class '{}' patched with .", ctClass.getName());
    }
}
