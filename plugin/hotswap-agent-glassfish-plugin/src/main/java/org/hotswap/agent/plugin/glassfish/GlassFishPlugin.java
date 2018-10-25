package org.hotswap.agent.plugin.glassfish;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * GlassFish Plugin
 *  - set boot delegation for hotswapagent's classes in felix class loader
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "GlassFish",
        description = "GlassFish - glassfish server.",
        testedVersions = {""},
        expectedVersions = {""},
        supportClass={WebappClassLoaderTransformer.class}
)
public class GlassFishPlugin {

    protected static AgentLogger LOGGER = AgentLogger.getLogger(GlassFishPlugin.class);

    private static String FRAMEWORK_BOOTDELEGATION = "org.osgi.framework.bootdelegation";

    private static final String BOOTDELEGATION_PACKAGES =
            "org.hotswap.agent, " +
            "org.hotswap.agent.*";


    @OnClassLoadEvent(classNameRegexp = "org.apache.felix.framework.Felix")
    public static void transformFelix(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("java.util.Map")
        };

        CtConstructor declaredConstructor = ctClass.getDeclaredConstructor(constructorParams);
        declaredConstructor.insertBefore(
                "{" +
                    "if ($1 == null) { " +
                        "$1 = new java.util.HashMap();" +
                    "}" +
                    "String __bootDeleg = (String) $1.get(\"" + FRAMEWORK_BOOTDELEGATION + "\");" +
                    "if (__bootDeleg == null) {" +
                        "__bootDeleg = \"\";" +
                    "}" +
                    "if (__bootDeleg.indexOf(\"org.hotswap.agent\") == -1) {" +
                        "__bootDeleg = __bootDeleg.trim();" +
                        "if (!__bootDeleg.isEmpty()) {" +
                            "__bootDeleg = __bootDeleg + \", \";" +
                        "}" +
                        "__bootDeleg = __bootDeleg + \"" + BOOTDELEGATION_PACKAGES + "\";" +
                        "$1.put(\"" + FRAMEWORK_BOOTDELEGATION + "\", __bootDeleg);" +
                    "}" +
                "}"
        );
//        declaredConstructor.insertAfter(PluginManagerInvoker.buildInitializePlugin(GlassFishPlugin.class));
        LOGGER.debug("Class 'org.apache.felix.framework.Felix' patched in classLoader {}.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.felix.framework.BundleWiringImpl")
    public static void transformBundleClassLoader(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {

//        String initializePlugin = PluginManagerInvoker.buildInitializePlugin(GlassFishPlugin.class);
//
//        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
//            constructor.insertAfter(initializePlugin);
//        }
//
        /*
        CtMethod getClassLoaderInternalCopy = CtMethod.make("public ClassLoader __getClassLoaderInternal(){return null;}", ctClass);
        getClassLoaderInternalCopy.setBody(ctClass.getDeclaredMethod("getClassLoaderInternal"), null);
        ctClass.addMethod(getClassLoaderInternalCopy);

        CtMethod getClassLoaderInternal = ctClass.getDeclaredMethod("getClassLoaderInternal");
        getClassLoaderInternal.setBody(
                "{  " +
                    "boolean wasClassLoader = (m_classLoader == null); " +
                    "ClassLoader ret = __getClassLoaderInternal();" +
                    "if (!wasClassLoader && ret != null) {" +
                        PluginManagerInvoker.buildInitializePlugin(GlassFishPlugin.class, "ret") +
                    "}" +
                    "return ret;" +
                "}"
        );

        LOGGER.debug("org.apache.felix.framework.BundleWiringImpl resource bundles registration.");
        */
    }
}
