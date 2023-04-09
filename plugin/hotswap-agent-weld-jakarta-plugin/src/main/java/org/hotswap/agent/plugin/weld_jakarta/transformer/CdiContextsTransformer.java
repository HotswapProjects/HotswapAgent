/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.weld_jakarta.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.weld_jakarta.beans.ContextualReloadHelper;
import org.hotswap.agent.plugin.weld_jakarta.beans.WeldHotswapContext;

/**
 * The Class CdiContextsTransformer.
 *
 * @author alpapad@gmail.com
 * @author Vladimir Dvorak
 */
public class CdiContextsTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(CdiContextsTransformer.class);

    public static final String BOUND_SESSION_BEAN_STORE_REGISTRY = "$$ha$boundSessionBeanStoreRegistry";

    /**
     * Add context reloading functionality to base contexts classes.
     *
     * @param classPool the class pool
     * @param ctClass   the class
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "(org.jboss.weld.context.AbstractManagedContext)|" +
                                        "(org.jboss.weld.context.AbstractSharedContext)|" +
                                        "(org.jboss.weld.context.unbound.DependentContextImpl)|" +
                                        "(org.jboss.weld.util.ForwardingContext)|" +
                                        "(org.apache.myfaces.flow.cdi.FlowScopedContextImpl)|" +
                                        "(org.apache.myfaces.cdi.view.ViewScopeContextImpl)"
                                        )
    public static void transformReloadingWeldContexts(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        if (!HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        LOGGER.debug("Adding interface {} to {}.", WeldHotswapContext.class.getName(), ctClass.getName());
        ctClass.addInterface(classPool.get(WeldHotswapContext.class.getName()));

        CtField toReloadFld = CtField.make("public transient java.util.Set $$ha$toReloadWeld = null;", ctClass);
        ctClass.addField(toReloadFld);

        CtField reloadingFld = CtField.make("public transient boolean $$ha$reloadingWeld = false;", ctClass);
        ctClass.addField(reloadingFld);

        CtMethod addBeanToReload = CtMethod.make(
                "public void $$ha$addBeanToReloadWeld(jakarta.enterprise.context.spi.Contextual bean) {" +
                    "if ($$ha$toReloadWeld == null)" +
                        "$$ha$toReloadWeld = new java.util.HashSet();" +
                    "$$ha$toReloadWeld.add(bean);" +
                "}",
                ctClass
        );
        ctClass.addMethod(addBeanToReload);

        CtMethod getBeansToReload = CtMethod.make("public java.util.Set $$ha$getBeansToReloadWeld(){return $$ha$toReloadWeld;}", ctClass);
        ctClass.addMethod(getBeansToReload);

        CtMethod reload = CtMethod.make("public void $$ha$reloadWeld() {" + ContextualReloadHelper.class.getName() +".reload(this);}", ctClass);
        ctClass.addMethod(reload);

        CtMethod isActive = ctClass.getDeclaredMethod("isActive");
        isActive.insertAfter(
                "{" +
                    "if($_ && !$$ha$reloadingWeld ) { " +
                        "$$ha$reloadingWeld = true;" +
                        "$$ha$reloadWeld();" +
                        "$$ha$reloadingWeld = false;" +
                    "}" +
                    "return $_;" +
                "}"
        );

        LOGGER.debug("Class '{}' patched with hot-swapping support", ctClass.getName() );
    }

    /**
     * Add custom tracker field to session context
     *
     * @param classPool the class pool
     * @param ctClass   the class
     * @throws NotFoundException      the not found exception
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.AbstractContext")
    public static void transformHttpSessionContext(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        if (!HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        HaCdiCommons.transformContext(classPool, ctClass);
    }
}
