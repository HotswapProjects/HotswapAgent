package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.BeanFactoryProcessor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bruce ge 2024/9/6
 */
public class BeanMetaDataTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(BeanMetaDataTransformer.class);


    public static Set<Object> metaDataTransformers = new HashSet<>();

    @OnClassLoadEvent(classNameRegexp = "org.hibernate.validator.internal.metadata.BeanMetaDataManagerImpl")
    public static void registerRemoveBeanMetaDataCache(ClassLoader appClassLoader, CtClass clazz,
                                                            ClassPool classPool) throws NotFoundException, CannotCompileException {
        // after construct BeanMetaDataManagerImpl, we need to the set.
        CtConstructor[] constructors = clazz.getConstructors();
        for (CtConstructor constructor : constructors) {
            //put BeanMetaDataTransformer.add(this) into the constructor.
            StringBuilder src = new StringBuilder("{");
            src.append("org.hotswap.agent.plugin.spring.transformers.BeanMetaDataTransformer.metaDataTransformers.add(this);");
            src.append("}");
            constructor.insertAfter(src.toString());
        }
    }


    @OnClassLoadEvent(classNameRegexp = "org.springframework.web.method.annotation.AbstractNamedValueArgumentResolver")
    public static void registerRemoveNamedValueInfoCache(ClassLoader appClassLoader, CtClass clazz,
                                                       ClassPool classPool) throws NotFoundException, CannotCompileException {
        // after construct BeanMetaDataManagerImpl, we need to the set.
        CtMethod getNamedValueInfo = clazz.getDeclaredMethod("getNamedValueInfo");
        if(getNamedValueInfo!=null) {
            StringBuilder src = new StringBuilder("{");
            src.append("this.namedValueInfoCache.remove($1);");
            src.append("}");
            getNamedValueInfo.insertBefore(src.toString());
            LOGGER.info("registerRemoveNamedValueInfoCache");
        }
    }


    @OnClassLoadEvent(classNameRegexp = "org.springframework.web.servlet.mvc.method.annotation.*ArgumentResolver")
    public static void registerRemovePathVariableInfoCache(ClassLoader appClassLoader, CtClass clazz,
                                                         ClassPool classPool) throws NotFoundException, CannotCompileException {
        // after construct BeanMetaDataManagerImpl, we need to the set.
        clearCacheForMethodAnnotation(clazz, classPool);
    }

    private static void clearCacheForMethodAnnotation(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        try {
            CtClass ctClass = classPool.get("org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver$NamedValueInfo");
            CtClass ctClass2 = classPool.get("org.springframework.core.MethodParameter");
            CtClass[] paramTypes = {ctClass2};
            String desc = Descriptor.ofMethod(ctClass, paramTypes);
            CtMethod getNamedValueInfo = clazz.getMethod("getNamedValueInfo", desc);
            if (getNamedValueInfo != null) {
                StringBuilder src = new StringBuilder("{");
                src.append("this.namedValueInfoCache.remove($1);");
                src.append("}");
                getNamedValueInfo.insertBefore(src.toString());
                LOGGER.info("registerRemoveNamedValueInfoCache for " + clazz.getName());
            }
        }catch (NotFoundException e){
            //ignore.
        }
    }


    @OnClassLoadEvent(classNameRegexp = "org.springframework.web.method.annotation.*ArgumentResolver")
    public static void registerRemoveResolverCache(ClassLoader appClassLoader, CtClass clazz,
                                                           ClassPool classPool) throws NotFoundException, CannotCompileException {
        // after construct BeanMetaDataManagerImpl, we need to the set.
        clearCacheForMethodAnnotation(clazz, classPool);
    }


    @OnClassLoadEvent(classNameRegexp = "org.springframework.web.method.support.HandlerMethodArgumentResolverComposite")
    public static void registerHandlerMethodArgumentResolverCompositeRemoveCache(ClassLoader appClassLoader, CtClass clazz,
                                                   ClassPool classPool) throws NotFoundException, CannotCompileException {
        // after construct BeanMetaDataManagerImpl, we need to the set.
        CtMethod getNamedValueInfo = clazz.getDeclaredMethod("getArgumentResolver");
        StringBuilder src = new StringBuilder("{");
        src.append("this.argumentResolverCache.remove($1);");
        src.append("}");
        getNamedValueInfo.insertBefore(src.toString());
        LOGGER.info("register Remove argumentResolverCache for " + clazz.getName());
    }


}
