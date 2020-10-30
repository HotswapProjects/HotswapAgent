/**
 *
 */
package org.hotswap.agent.plugin.myfaces.transformer;

import static org.hotswap.agent.plugin.myfaces.MyFacesConstants.MANAGED_BEAN_RESOLVER_CLASS;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.myfaces.MyFacesConstants;

/**
 * A transformer which modifies {@link org.apache.myfaces.el.unified.resolver.ManagedBeanResolver} class.
 *
 * <p>This transformer adds functionality to hold and reload the dirty managed beans.
 *
 * @author sinan.yumak
 *
 */
public class ManagedBeanResolverTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ManagedBeanResolverTransformer.class);

    public static final String DIRTY_BEANS_FIELD = "DIRTY_BEANS";

    public static CtClass MODIFIED_MANAGED_BEAN_RESOLVER;


    @OnClassLoadEvent(classNameRegexp = MANAGED_BEAN_RESOLVER_CLASS)
    public static void init(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
        LOGGER.info("Patching managed bean resolver. Class loader: {}", classLoader);

        initClassPool(ctClass);

        createDirtyBeansField(ctClass);
        createAddToDirtyBeansMethod(ctClass);
        createGetManagedBeanInfosMethod(ctClass);
        createUpdateRuntimeConfigMethod(ctClass);
        createCreateDirtyManagedBeansMethod(ctClass);
        createProcessDirtyBeansMethod(ctClass);

        LOGGER.info("Patched managed bean resolver successfully.");
        MODIFIED_MANAGED_BEAN_RESOLVER = ctClass;
    }

    private static void initClassPool(CtClass ctClass) {
        ClassPool classPool = ctClass.getClassPool();

        classPool.importPackage("java.lang");
        classPool.importPackage("java.util");
        classPool.importPackage("java.util.logging");
        classPool.importPackage("org.apache.myfaces.config.annotation");
        classPool.importPackage("org.apache.myfaces.config.impl.digester.elements");
        classPool.importPackage("org.hotswap.agent.util");
        classPool.importPackage("javax.faces.context");
        classPool.importPackage("org.apache.myfaces.config");
        classPool.importPackage("org.apache.myfaces.config.element");
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
                "log.log(Level.INFO, \"Added to dirty beans.\");" +
            "}",
            ctClass
        );

        ctClass.addMethod(addToDirtyBeansMethod);
    }

    /**
     * Creates a method which returns managed bean infos with the
     * {@link org.apache.myfaces.config.element.FacesConfig} format.
     */
    private static void createGetManagedBeanInfosMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod getManagedBeanInfosMethod = CtMethod.make(
            "public FacesConfig getManagedBeanInfos() {" +

                "FacesConfig facesConfig = new FacesConfig(); " +
                "Set dirtyBeansSet = new HashSet(" + DIRTY_BEANS_FIELD + "); "+
                "log.log(Level.FINE, \"Dirty managed bean infos:\" + dirtyBeansSet);" +

                "AnnotationConfigurator ac = new AnnotationConfigurator(); " +
                "ReflectionHelper.invoke(ac, " +
                        "AnnotationConfigurator.class, " +
                        "\"handleManagedBean\", " +
                        "new Class[] {FacesConfig.class, Set.class}, " +
                        "new Object[] {facesConfig, dirtyBeansSet} " +
                "); " +

                "log.log(Level.FINE, \"Got managed bean infos. Faces config :\" + facesConfig.getManagedBeans());" +
                "return facesConfig;" +
            "}",
            ctClass
        );

        ctClass.addMethod(getManagedBeanInfosMethod);
    }

    /**
     * Creates a method which updates {@link org.apache.myfaces.config.RuntimeConfig} with
     * the dirty managed bean infos.
     */
    private static void createUpdateRuntimeConfigMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod updateRuntimeConfigMethod = CtMethod.make(
            "public void updateRuntimeConfig(FacesConfig facesConfig) {" +

                "ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext(); " +
                "RuntimeConfig runtimeConfig = RuntimeConfig.getCurrentInstance(externalContext); " +

                "List managedBeans = facesConfig.getManagedBeans(); " +

                "Iterator iterator = managedBeans.iterator(); "+
                "while (iterator.hasNext()) {" +

                    "ManagedBean dirtyBean = (ManagedBean)iterator.next(); " +
                    "runtimeConfig.addManagedBean(dirtyBean.getManagedBeanName(), dirtyBean); " +

                "} "+

                "log.log(Level.FINE, \"Updated Runtime Config managed bean definitions.\");" +
            "}",
            ctClass
        );

        ctClass.addMethod(updateRuntimeConfigMethod);
    }

    /**
     * Creates a method which creates managed bean instances with the {@link org.apache.myfaces.config.element.FacesConfig}.
     */
    private static void createCreateDirtyManagedBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod createDirtyManagedBeansMethod = CtMethod.make(
            "public void createDirtyManagedBeans(FacesConfig facesConfig) {" +

                "FacesContext facesContext = FacesContext.getCurrentInstance(); " +

                "List managedBeans = facesConfig.getManagedBeans(); " +

                "Iterator iterator = managedBeans.iterator(); "+
                "while (iterator.hasNext()) {" +

                    "ManagedBean dirtyBean = (ManagedBean)iterator.next(); " +
                    "this.createManagedBean(dirtyBean, facesContext); " +

                    "iterator.remove();" +

                    "log.log(Level.INFO, \"Reloaded managed bean. Bean name: \" + dirtyBean.getManagedBeanName());" +
                "} "+

                "log.log(Level.FINE, \"Created dirty managed beans.\");" +
            "}",
            ctClass
        );

        ctClass.addMethod(createDirtyManagedBeansMethod);
    }

    /**
     * Creates a method which processes the dirty beans.
     */
    private static void createProcessDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod processDirtyBeansMethod = CtMethod.make(
            "public synchronized void processDirtyBeans() {" +

                "if ("+ DIRTY_BEANS_FIELD + ".isEmpty()) { " +
                    "log.log(Level.FINE, \"No dirty bean found. Returning.\");" +
                    "return; " +
                "} " +

                "FacesContext facesContext = FacesContext.getCurrentInstance(); " +
                "if (facesContext == null) { "+
                    "log.log(Level.WARNING, \"Faces context is null. Returning.\");" +
                    "return;" +
                "}" +

                "FacesConfig facesConfig = getManagedBeanInfos(); " +

                "this.updateRuntimeConfig(facesConfig); " +
                "this.createDirtyManagedBeans(facesConfig); " +

                DIRTY_BEANS_FIELD + ".clear(); " +

                "log.log(Level.INFO, \"Processed dirty beans.\");" +
            "}",
            ctClass
        );

        ctClass.addMethod(processDirtyBeansMethod);
    }

    public static synchronized CtClass getModifiedCtClass(ClassPool classPool) throws CannotCompileException, NotFoundException {
        if (MODIFIED_MANAGED_BEAN_RESOLVER == null) {
            CtClass resolverClass = classPool.get(MyFacesConstants.MANAGED_BEAN_RESOLVER_CLASS);
            init(resolverClass, classPool.getClassLoader());
        }

        return MODIFIED_MANAGED_BEAN_RESOLVER;
    }

}
