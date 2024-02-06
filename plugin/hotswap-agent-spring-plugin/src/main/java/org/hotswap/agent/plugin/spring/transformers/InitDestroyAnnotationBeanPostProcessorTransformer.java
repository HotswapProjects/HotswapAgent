package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.support.InitMethodEnhance;

public class InitDestroyAnnotationBeanPostProcessorTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(
        InitDestroyAnnotationBeanPostProcessorTransformer.class);

    @OnClassLoadEvent(
        classNameRegexp = "org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        LOGGER.debug(
            "Class 'org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor' patched with"
                + " catch exception.");
        clazz.addField(CtField.make("private static final org.hotswap.agent.logging.AgentLogger $$ha$LOGGER = " +
                "org.hotswap.agent.logging.AgentLogger.getLogger(org.springframework.beans.factory.annotation"
                + ".InitDestroyAnnotationBeanPostProcessor.class);",
            clazz));
        CtMethod method = clazz.getDeclaredMethod("postProcessBeforeInitialization",
            new CtClass[] {classPool.get("java.lang.Object"), classPool.get("java.lang.Object")});
        method.addCatch(InitMethodEnhance.catchException("$1", "$$ha$LOGGER", "$e", true),
            classPool.get("java.lang.Throwable"));
    }
}
