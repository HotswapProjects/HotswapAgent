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
package org.hotswap.agent.plugin.myfaces.transformer;

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

    @OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.cdi.view.ViewScopeContextImpl")
    public static void patchViewScopeContext(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        HaCdiCommons.transformContext(classPool, ctClass);
    }

}
