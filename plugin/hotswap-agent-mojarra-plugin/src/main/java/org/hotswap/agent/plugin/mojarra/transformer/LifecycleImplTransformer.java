/**
 * 
 */
package org.hotswap.agent.plugin.mojarra.transformer;

import static org.hotswap.agent.plugin.mojarra.MojarraConstants.LIFECYCLE_IMPL_CLASS;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;


/**
 * A transformer which modifies {@link com.sun.faces.lifecycle.LifecycleImpl} class.
 *
 * <p>This transformer adds an hook to process dirty managed beans.
 *
 * @author sinan.yumak
 *
 */
public class LifecycleImplTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(LifecycleImplTransformer.class);

    
    @OnClassLoadEvent(classNameRegexp = LIFECYCLE_IMPL_CLASS)
    public static void init(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
        LOGGER.info("Patching lifecycle implementation. classLoader: {}", classLoader);

        initClassPool(ctClass);
        patchExecuteMethod(ctClass, classLoader);

        LOGGER.info("Patched lifecycle implementation successfully.");
    }

    private static void initClassPool(CtClass ctClass) throws CannotCompileException, NotFoundException {
        ClassPool classPool = ctClass.getClassPool();
        
        CtClass modifiedManagerCtClass = BeanManagerTransformer.getModifiedCtClass(classPool);
        
        modifiedManagerCtClass.defrost();
        classPool.makeClass(modifiedManagerCtClass.getClassFile());

        classPool.importPackage("com.sun.faces.application");
        classPool.importPackage("com.sun.faces.mgbean");
    }
    
    /**
     * Patches the {@link org.apache.myfaces.lifecycle.LifecycleImpl#execute} to process dirty managed beans.
     */
    private static void patchExecuteMethod(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
        ClassPool classPool = ctClass.getClassPool();
        
        CtMethod renderMethod = ctClass.getDeclaredMethod("execute", new CtClass[] {
            classPool.get("javax.faces.context.FacesContext"),
        });

        String processDirtyBeanCall = 
            "ApplicationAssociate application = ApplicationAssociate.getCurrentInstance(); " +
            "BeanManager beanManager = application.getBeanManager(); " +
            "beanManager.processDirtyBeans(); "
            ;
        
        renderMethod.insertAfter(processDirtyBeanCall);
    }

}
