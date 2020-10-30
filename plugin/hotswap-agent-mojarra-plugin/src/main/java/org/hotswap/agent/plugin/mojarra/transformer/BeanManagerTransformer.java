/**
 *
 */
package org.hotswap.agent.plugin.mojarra.transformer;

import static org.hotswap.agent.plugin.mojarra.MojarraConstants.BEAN_MANAGER_CLASS;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mojarra.MojarraConstants;


/**
 * A transformer which modifies {@link com.sun.faces.mgbean.BeanManager} class.
 *
 * <p>This transformer adds functionality to hold and reload the dirty managed beans.
 *
 * @author sinan.yumak
 *
 */
public class BeanManagerTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanManagerTransformer.class);

    public static final String DIRTY_BEANS_FIELD = "DIRTY_BEANS";

    public static CtClass MODIFIED_BEAN_MANAGER;


    @OnClassLoadEvent(classNameRegexp = BEAN_MANAGER_CLASS)
    public static void init(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
        LOGGER.info("Patching bean manager. Class loader: {}", classLoader);

        initClassPool(ctClass);
        createDirtyBeansField(ctClass);

        createAddToDirtyBeansMethod(ctClass);
        createGetManagedBeanInfoMethod(ctClass);
        createProcessDirtyBeansMethod(ctClass);

        LOGGER.info("Patched bean manager successfully.");
        MODIFIED_BEAN_MANAGER = ctClass;
    }

    private static void initClassPool(CtClass ctClass) {
        ClassPool classPool = ctClass.getClassPool();

        classPool.importPackage("com.sun.faces.mgbean");
        classPool.importPackage("com.sun.faces.application.annotation");
        classPool.importPackage("java.lang");
        classPool.importPackage("java.util");
        classPool.importPackage("java.util.concurrent");
        classPool.importPackage("java.util.logging");
        classPool.importPackage("javax.faces.context");
        classPool.importPackage("javax.faces.bean");
        classPool.importPackage("org.hotswap.agent.util");
    }

    /**
     * Creates a field which holds dirty beans.
     */
    private static void createDirtyBeansField(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtField dirtyBeansField = CtField.make(
            "public static List " + DIRTY_BEANS_FIELD + " = new ArrayList();" , ctClass
        );
        ctClass.addField(dirtyBeansField);
    }

    /**
     * Creates a method which adds a managed bean class to dirty beans list.
     */
    private static void createAddToDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod addToDirtyBeansMethod = CtMethod.make(
            "public static synchronized void addToDirtyBeans(Class beanClass) {" +

                DIRTY_BEANS_FIELD + ".add(beanClass);" +

                "LOGGER.log(Level.INFO, \"Added to dirty beans.\");" +
            "}",
            ctClass
        );

        ctClass.addMethod(addToDirtyBeansMethod);
    }

    /**
     * Creates a method which returns managed bean info with the
     * {@link com.sun.faces.mgbean.ManagedBeanInfo.ManagedBeanInfo} format.
     */
    private static void createGetManagedBeanInfoMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod getManagedBeanInfoMethod = CtMethod.make(
            "public ManagedBeanInfo getManagedBeanInfo(Class beanClass) { " +

                "ManagedBeanConfigHandler configHandler = new ManagedBeanConfigHandler(); " +

                "Object beanInfo = " +
                "ReflectionHelper.invoke(configHandler, " +
                    "ManagedBeanConfigHandler.class, " +
                    "\"getBeanInfo\", " +
                    "new Class[] {Class.class, ManagedBean.class}, " +
                    "new Object[] {beanClass, beanClass.getAnnotation(ManagedBean.class)} " +
                "); " +

                "LOGGER.log(Level.FINE, \"Got managed bean info. Bean Info: \" + beanInfo);" +
                "return (ManagedBeanInfo)beanInfo;" +
            "}",
            ctClass
        );

        ctClass.addMethod(getManagedBeanInfoMethod);
    }

    /**
     * Creates a method which processes the dirty beans.
     */
    private static void createProcessDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod processDirtyBeansMethod = CtMethod.make(
            "public synchronized void processDirtyBeans() {" +

                "FacesContext facesContext = FacesContext.getCurrentInstance(); " +
                "if (facesContext == null) { "+
                    "return;" +
                "}" +

                "Iterator iterator = " + DIRTY_BEANS_FIELD + ".iterator(); "+
                "while (iterator.hasNext()) {" +

                    "Class beanClass = (Class)iterator.next(); " +

                    "ManagedBeanInfo beanInfo = this.getManagedBeanInfo(beanClass); " +
                    "this.register(beanInfo); " +

                    "String beanName = beanInfo.getName(); " +
                    "BeanBuilder beanBuilder = this.getBuilder(beanName);" +

                    "this.preProcessBean(beanName, beanBuilder); " +
                    "this.create(beanName, facesContext); " +

                    "iterator.remove();" +

                    "LOGGER.log(Level.INFO, \"Reloaded managed bean. Bean name: \" + beanName);" +
                "} "+

                "LOGGER.log(Level.FINE, \"Processed dirty beans.\");" +
            "}",
            ctClass
        );

        ctClass.addMethod(processDirtyBeansMethod);
    }

    public static synchronized CtClass getModifiedCtClass(ClassPool classPool) throws CannotCompileException, NotFoundException {
        if (MODIFIED_BEAN_MANAGER == null) {
            CtClass resolverClass = classPool.get(MojarraConstants.BEAN_MANAGER_CLASS);
            init(resolverClass, classPool.getClassLoader());
        }

        return MODIFIED_BEAN_MANAGER;
    }


}
