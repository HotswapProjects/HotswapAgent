package org.hotswap.agent.plugin.omnifaces;


import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Register plugin and patch ViewScopeStorageInSession
 *
 * @author Vladimir Dvorak
 */
public class OmnifacesTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(OmnifacesTransformer.class);

    public static final String CUSTOM_CONTEXT_TRACKER_FIELD = "$$ha$customContextTrackers";
    public static final String HA_BEAN_STORAGE_ID = "$$ha$beanStorageId";
    public static final String HA_FORCE_IS_ACTIVE = "$$ha$forceIsActive";

    @OnClassLoadEvent(classNameRegexp = "org.omnifaces.ApplicationListener")
    public static void patchApplicationListener(CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.getDeclaredMethod("contextInitialized").insertAfter(
            "{" +
                PluginManagerInvoker.buildInitializePlugin(OmnifacesPlugin.class) +
                PluginManagerInvoker.buildCallPluginMethod(OmnifacesPlugin.class, "init") +
            "}"
        );
    }

    @OnClassLoadEvent(classNameRegexp = "org.omnifaces.cdi.viewscope.ViewScopeStorageInSession")
    public static void patchViewScopeStorageInSession(CtClass ctClass) throws CannotCompileException, NotFoundException {

        ctClass.getDeclaredMethod("postConstructSession")
            .insertAfter("org.hotswap.agent.plugin.omnifaces.ViewContextTracker.register();");

        CtField beanStorageIdField = CtField.make("private java.util.UUID " + HA_BEAN_STORAGE_ID + ";", ctClass);
        ctClass.addField(beanStorageIdField);

        ctClass.getDeclaredMethod("getBeanStorageId")
            .insertBefore( "if(this." + HA_BEAN_STORAGE_ID + "!=null){return this." + HA_BEAN_STORAGE_ID + ";}");

        LOGGER.debug("org.omnifaces.cdi.viewscope.ViewScopeStorageInSession - patched by view context tracker hooks.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.omnifaces.cdi.viewscope.ViewScopeContext")
    public static void patchViewScopeContext(CtClass ctClass) throws CannotCompileException, NotFoundException {

        CtField beanStorageIdField = CtField.make("public static boolean " + HA_FORCE_IS_ACTIVE + ";", ctClass);
        ctClass.addField(beanStorageIdField);

        ctClass.getDeclaredMethod("isActive")
            .insertBefore( "if(" + HA_FORCE_IS_ACTIVE + "){return true;}");

        LOGGER.debug("org.omnifaces.cdi.viewscope.ViewScopeStorageInSession - patched by view context tracker hooks.");
    }

}
