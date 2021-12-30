package org.hotswap.agent.plugin.cxf.jaxrs;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * The Class CxfJAXRSTransformer.
 */
public class CxfJAXRSTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CxfJAXRSTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.apache.cxf.jaxrs.utils.ResourceUtils")
    public static void patchResourceUtils(CtClass ctClass, ClassPool classPool){
        try{
            CtMethod createCriMethods[] = ctClass.getDeclaredMethods("createClassResourceInfo");

            for (CtMethod method: createCriMethods) {
                if (method.getParameterTypes()[0].getName().equals(Class.class.getName())) {
                    method.insertAfter(
                        "if($_ != null && !$_.getClass().getName().contains(\"$$\") ) { " +
                            "ClassLoader $$cl = java.lang.Thread.currentThread().getContextClassLoader();" +
                            "if ($$cl==null) $$cl = $1.getClassLoader();" +
                            PluginManagerInvoker.buildInitializePlugin(CxfJAXRSPlugin.class, "$$cl") +
                            "try {" +
                                org.hotswap.agent.javassist.runtime.Desc.class.getName() + ".setUseContextClassLoaderLocally();" +
                                "$_ = " + ClassResourceInfoProxyHelper.class.getName() + ".createProxy($_, $sig, $args);" +
                            "} finally {"+
                                org.hotswap.agent.javassist.runtime.Desc.class.getName() + ".resetUseContextClassLoaderLocally();" +
                            "}" +
                            "if ($_.getClass().getName().contains(\"$$\")) {" +
                                 PluginManagerInvoker.buildCallPluginMethod("$$cl", CxfJAXRSPlugin.class, "registerClassResourceInfo",
                                "$_.getServiceClass()", "java.lang.Class", "$_", "java.lang.Object") +
                            "}" +
                        "}" +
                        "return $_;"
                    );
                }
            }
        } catch(NotFoundException | CannotCompileException e){
            LOGGER.error("Error patching ResourceUtils", e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.cxf.jaxrs.model.ClassResourceInfo")
    public static void patchClassResourceInfo(CtClass ctClass, ClassPool classPool) {
        try {
            // Add default constructor used in proxy creation
            CtConstructor c = CtNewConstructor.make("public " + ctClass.getSimpleName() + "() { super(null); }", ctClass);
            ctClass.addConstructor(c);
        } catch (CannotCompileException e) {
            LOGGER.error("Error patching ClassResourceInfo", e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.cxf.cdi.JAXRSCdiResourceExtension")
    public static void patchCxfJARSCdiExtension(CtClass ctClass, ClassPool classPool){
        try{
            CtMethod loadMethod = ctClass.getDeclaredMethod("load");

            loadMethod.insertAfter( "{ " +
                    "ClassLoader $$cl = java.lang.Thread.currentThread().getContextClassLoader();" +
                    "if ($$cl==null) $$cl = this.bus.getClass().getClassLoader();" +
                    "Object $$plugin =" + PluginManagerInvoker.buildInitializePlugin(CxfJAXRSPlugin.class, "$$cl") +
                    HaCdiExtraCxfContext.class.getName() + ".registerExtraContext($$plugin);" +
                "}"
            );
    } catch(NotFoundException | CannotCompileException e){
            LOGGER.error("Error patching ResourceUtils", e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.cxf.jaxrs.spring.SpringResourceFactory")
    public static void patchSpringResourceFactory(CtClass ctClass, ClassPool classPool){
        try{
            CtMethod loadMethod = ctClass.getDeclaredMethod("getInstance");
            loadMethod.insertBefore( "{ " +
                    "if(isSingleton() && this.singletonInstance==null){ " +
                        "try{" +
                            "this.singletonInstance=ac.getBean(beanId);" +
                        "}catch (Exception ex) {}" +
                    "}" +
                "}"
            );
            ctClass.addMethod(CtMethod.make(
                    "public void clearSingletonInstance() { this.singletonInstance=null; }", ctClass));
    } catch(NotFoundException | CannotCompileException e){
            LOGGER.error("Error patching ResourceUtils", e);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.cxf.jaxrs.provider.AbstractJAXBProvider")
    public static void patchAbstractJAXBProvider(CtClass ctClass, ClassPool classPool){
        try{
            CtMethod loadMethod = ctClass.getDeclaredMethod("init");
            loadMethod.insertAfter( "{ " +
                    "ClassLoader $$cl = java.lang.Thread.currentThread().getContextClassLoader();" +
                    "if ($$cl==null) $$cl = getClass().getClassLoader();" +
                    PluginManagerInvoker.buildInitializePlugin(CxfJAXRSPlugin.class, "$$cl") +
                    PluginManagerInvoker.buildCallPluginMethod("$$cl", CxfJAXRSPlugin.class, "registerJAXBProvider",
                                "this", "java.lang.Object") +
                "}"
            );
    } catch(NotFoundException | CannotCompileException e){
            LOGGER.error("Error patching ResourceUtils", e);
        }
    }

}
