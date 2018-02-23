package org.hotswap.agent.plugin.myfaces;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

/**
 * Patch ViewScopeBeanHolder and ViewScopeContextImpl
 *
 * @author Vladimir Dvorak
 */
public class MyFacesTransformer {

    @OnClassLoadEvent(classNameRegexp = "(org.apache.myfaces.cdi.view.ViewScopeContextImpl)|" +
                                        "(org.omnifaces.cdi.viewscope.ViewScopeContext)")
    public static void patchViewScopeContext(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        HaCdiCommons.transformContext(classPool, ctClass);
    }

}
