package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Hook into classpath scanner process to register basicPackage of scanned classes.
 *
 * Catch changes on component-scan configuration such as (see tests):
 * <pre>&lt;context:component-scan base-package="org.hotswap.agent.plugin.spring.testBeans"/&gt;</pre>
 */
public class XmlBeanDefinitionScannerTransformer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanDefinitionScannerTransformer.class);

    /**
     * Insert at the beginning of the method:
     * <pre>public Set<BeanDefinition> findCandidateComponents(String basePackage)</pre>
     * new code to initialize ClassPathBeanDefinitionScannerAgent for a base class
     * It would be better to override a more appropriate method
     * org.springframework.context.annotation.ClassPathBeanDefinitionScanner.scan() directly,
     * however there are issues with javassist and varargs parameters.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.xml.XmlBeanDefinitionReader")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtMethod method = clazz.getDeclaredMethod("loadBeanDefinitions", new CtClass[]{classPool.get("org.springframework.core.io.support.EncodedResource")});
        method.insertAfter("org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinationScannerAgent." +
                "registerXmlBeanDefinationScannerAgent(this, $1.getResource().getURL());");

        LOGGER.debug("Class 'org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider' patched with basePackage registration.");
    }
}
