package org.hotswap.agent.plugin.spring.boot.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class PropertySourceTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertySourceTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.core.env.MapPropertySource")
    public static void transformMapPropertySource(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        transformPropertySource(clazz, classPool);
        LOGGER.debug("Patch org.springframework.boot.env.MapPropertySource success");
    }

    @OnClassLoadEvent(classNameRegexp= "org.springframework.boot.env.OriginTrackedMapPropertySource")
    public static void transformOriginTrackedMapPropertySource(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        transformPropertySource(clazz, classPool);
        LOGGER.debug("Patch org.springframework.boot.env.OriginTrackedMapPropertySource success");
    }

    private static void transformPropertySource(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.IReloadPropertySource"));
        clazz.addField(CtField.make("private org.hotswap.agent.plugin.spring.api.PropertySourceReload reload;", clazz));

        clazz.addMethod(CtMethod.make("public void setReload(org.hotswap.agent.plugin.spring.api.PropertySourceReload r) { this.reload = r; }", clazz));
        clazz.addMethod(CtMethod.make("public void reload() { if (this.reload != null) {this.reload.reload();} }", clazz));
    }
}
