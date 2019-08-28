package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringPlugin;

/**
 * Hook into classpath scanner process to register basicPackage of scanned classes.
 *
 * Catch changes on component-scan configuration such as (see tests):
 * <pre>&lt;context:component-scan base-package="org.hotswap.agent.plugin.spring.testBeans"/&gt;</pre>
 */
public class ClassPathBeanDefinitionScannerTransformer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanDefinitionScannerTransformer.class);

    /**
     * Insert at the beginning of the method:
     * <pre>public Set<BeanDefinition> findCandidateComponents(String basePackage)</pre>
     * new code to initialize ClassPathBeanDefinitionScannerAgent for a base class
     * It would be better to override a more appropriate method
     * org.springframework.context.annotation.ClassPathBeanDefinitionScanner.scan() directly,
     * however there are issues with javassist and varargs parameters.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (SpringPlugin.basePackagePrefixes == null) {
            CtMethod method = clazz.getDeclaredMethod("findCandidateComponents", new CtClass[]{classPool.get("java.lang.String")});
            method.insertAfter("if (this instanceof org.springframework.context.annotation.ClassPathBeanDefinitionScanner) {" +
                    "org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent." +
                    "getInstance((org.springframework.context.annotation.ClassPathBeanDefinitionScanner)this)." +
                    "registerBasePackage($1);" +
                    "}");

            LOGGER.debug("Class 'org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider' patched with basePackage registration.");
        } else {
            LOGGER.debug("No need to register scanned path, instead just register 'spring.basePackagePrefix' in configuration file.");
        }
    }
}
