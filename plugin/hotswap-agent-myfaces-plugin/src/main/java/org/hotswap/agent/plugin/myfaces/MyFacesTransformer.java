package org.hotswap.agent.plugin.myfaces;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

/**
 * Patch ViewScopeBeanHolder and ViewScopeContextImpl
 *
 * @author Vladimir Dvorak
 */
public class MyFacesTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MyFacesTransformer.class);

    public static final String HA_FORCE_IS_ACTIVE = "$$ha$forceIsActive";
    public static final String HA_CURRENT_VIEW_SCOPE_ID = "$$ha$currentViewScopeId";
    public static final String HA_CURRENT_VIEW_SCOPE_STORAGE = "$$ha$currentViewScopeStorage";

    @OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.cdi.view.ViewScopeBeanHolder")
    public static void patchViewScopeStorageInSession(CtClass ctClass) throws CannotCompileException, NotFoundException {

        ctClass.getDeclaredMethod("init")
            .insertAfter("org.hotswap.agent.plugin.myfaces.ViewContextTracker.register();");

        ctClass.addMethod(CtNewMethod.make("public void " + HaCdiCommons.ATTACH_CUSTOM_CONTEXT_TRACKER_METHOD + "(Object ctx) {" +
                    "org.hotswap.agent.plugin.myfaces.ViewContextTracker.attach(ctx);" +
                "}", ctClass));

        LOGGER.debug("org.apache.myfaces.cdi.view.ViewScopeBeanHolder - patched by view context tracker hooks.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.cdi.view.ViewScopeContextImpl")
    public static void patchViewScopeContext(CtClass ctClass) throws CannotCompileException, NotFoundException {

        CtField forceIsActiveField = CtField.make("public static boolean " + HA_FORCE_IS_ACTIVE + ";", ctClass);
        ctClass.addField(forceIsActiveField);

        CtField currentViewScopeId = CtField.make("public static String " + HA_CURRENT_VIEW_SCOPE_ID + ";", ctClass);
        ctClass.addField(currentViewScopeId);

        CtField currentViewScopeStorage = CtField.make("public static java.lang.Object " + HA_CURRENT_VIEW_SCOPE_STORAGE + ";", ctClass);
        ctClass.addField(currentViewScopeStorage);

        ctClass.getDeclaredMethod("isActive")
            .insertBefore( "if(" + HA_FORCE_IS_ACTIVE + "){return true;}");

        ctClass.getDeclaredMethod("getCurrentViewScopeId")
            .insertBefore( "if(" + HA_CURRENT_VIEW_SCOPE_ID + "!=null){return " + HA_CURRENT_VIEW_SCOPE_ID + ";}");

        ctClass.getDeclaredMethod("getContextualStorage").insertBefore(
            "if(" + HA_CURRENT_VIEW_SCOPE_STORAGE + "!=null){" +
                "return (org.apache.myfaces.cdi.view.ViewScopeContextualStorage)" + HA_CURRENT_VIEW_SCOPE_STORAGE + ";" +
            "}"
        );


        LOGGER.debug("org.apache.myfaces.cdi.view.ViewScopeContextImpl - patched by view context tracker hooks.");
    }

}
