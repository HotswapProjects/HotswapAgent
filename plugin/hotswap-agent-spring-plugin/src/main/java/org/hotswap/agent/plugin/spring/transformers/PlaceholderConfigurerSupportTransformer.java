package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

import java.util.Arrays;

public class PlaceholderConfigurerSupportTransformer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PlaceholderConfigurerSupportTransformer.class);

    /**
     * Insert at the beginning of the method:
     * <pre>public Set<BeanDefinition> findCandidateComponents(String basePackage)</pre>
     * new code to initialize ClassPathBeanDefinøitionScannerAgent for a base class
     * It would be better to override a more appropriate method
     * org.springframework.context.annotation.ClassPathBeanDefinitionScanner.scan() directly,
     * however there are issues with javassist and varargs parameters.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.config.PlaceholderConfigurerSupport")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        for (CtClass interfaceClazz : clazz.getInterfaces()) {
            if (interfaceClazz.getName().equals("org.hotswap.agent.plugin.spring.transformers.api.IPlaceholderConfigurerSupport")) {
                return;
            }
        }
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.IPlaceholderConfigurerSupport"));
        clazz.addField(CtField.make("private java.util.List _resolvers;", clazz), "new java.util.ArrayList(2)");
        clazz.addMethod(CtMethod.make("public java.util.List valueResolvers() { return this._resolvers; }", clazz));
        CtMethod ctMethod = clazz.getDeclaredMethod("doProcessProperties", new CtClass[]{classPool.get("org.springframework.beans.factory.config.ConfigurableListableBeanFactory"),
                classPool.get("org.springframework.util.StringValueResolver")});
        ctMethod.insertBefore("org.hotswap.agent.plugin.spring.SpringChangedHub.collectPlaceholderProperties($1); " +
                "this._resolvers.add($2);");
        LOGGER.debug("class 'org.springframework.beans.factory.config.PlaceholderConfigurerSupport' patched with placeholder keep.");
    }
}
