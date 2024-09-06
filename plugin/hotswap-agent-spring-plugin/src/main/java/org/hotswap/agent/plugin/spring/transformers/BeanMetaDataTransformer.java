package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.plugin.spring.core.BeanFactoryProcessor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bruce ge 2024/9/6
 */
public class BeanMetaDataTransformer {

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
//        clazz.addField(
//                CtField.make("private java.util.Set hotSwapAgent$reloadedClasses = new java.util.HashSet();", clazz));
//        CtClass[] params = {
//                classPool.get(Class.class.getName())};
//        CtMethod getBeanMetaData = clazz.getDeclaredMethod("getBeanMetaData", params);
//        getBeanMetaData.insertBefore();
//        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.spring.transformers.api.BeanFactoryLifecycle"));
//        clazz.addMethod(CtNewMethod.make(
//                "public boolean hotswapAgent$isDestroyedBean(String beanName) { return hotswapAgent$destroyBean.contains"
//                + "(beanName); }",
//                clazz));
//        clazz.addMethod(CtNewMethod.make(
//                "public void hotswapAgent$destroyBean(String beanName) { hotswapAgent$destroyBean.add(beanName); }",
//                clazz));
//        clazz.addMethod(
//                CtNewMethod.make("public void hotswapAgent$clearDestroyBean() { hotswapAgent$destroyBean.clear(); }",
//                        clazz));
//
//        CtMethod destroySingletonMethod = clazz.getDeclaredMethod("destroySingleton",
//                new CtClass[] {classPool.get(String.class.getName())});
//        destroySingletonMethod.insertAfter(
//                BeanFactoryProcessor.class.getName() + ".postProcessDestroySingleton($0, $1);");
    }
}
