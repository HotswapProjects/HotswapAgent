package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

public class ResourcePropertySourceTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ResourcePropertySourceTransformer.class);

    /**
     * Insert at the beginning of the method:
     * <pre>public Set<BeanDefinition> findCandidateComponents(String basePackage)</pre>
     * new code to initialize ClassPathBeanDefinøitionScannerAgent for a base class
     * It would be better to override a more appropriate method
     * org.springframework.context.annotation.ClassPathBeanDefinitionScanner.scan() directly,
     * however there are issues with javassist and varargs parameters.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.core.io.support.ResourcePropertySource")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.ReloadableResourcePropertySource"));
        clazz.addField(CtField.make("private org.springframework.core.io.support.EncodedResource encodedResource;", clazz));
        clazz.addField(CtField.make("private org.springframework.core.io.Resource resource;", clazz));
        CtConstructor ctConstructor0 = clazz.getDeclaredConstructor(new CtClass[]{classPool.get("java.lang.String"),
                classPool.get("org.springframework.core.io.support.EncodedResource")});
        ctConstructor0.insertBefore("this.encodedResource = $2;");
        CtConstructor ctConstructor1 = clazz.getDeclaredConstructor(new CtClass[]{classPool.get("org.springframework.core.io.support.EncodedResource")});
        ctConstructor1.insertBefore("this.encodedResource = $1;");

        CtConstructor ctConstructor2 = clazz.getDeclaredConstructor(new CtClass[]{classPool.get("java.lang.String"),
                classPool.get("org.springframework.core.io.Resource")});
        ctConstructor2.insertBefore("this.resource = $2;");

        CtConstructor ctConstructor3 = clazz.getDeclaredConstructor(new CtClass[]{classPool.get("org.springframework.core.io.Resource")});
        ctConstructor3.insertBefore("this.resource = $1;");

        clazz.addMethod(CtMethod.make("public org.springframework.core.io.support.EncodedResource encodedResource() { return this.encodedResource; }", clazz));
        clazz.addMethod(CtMethod.make("public org.springframework.core.io.Resource resource() { return this.resource; }", clazz));

        LOGGER.debug("class 'org.springframework.core.io.support.DefaultPropertySourceFactory' patched with PropertySource keep.");
    }
}
