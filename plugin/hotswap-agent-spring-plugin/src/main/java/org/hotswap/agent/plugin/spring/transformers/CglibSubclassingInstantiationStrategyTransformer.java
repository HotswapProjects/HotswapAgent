/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;

/**
 * Tells ProxyReplacer which beans Spring has created as a CGLIB subclass with lookup or replaced methods (@Lookup,
 * lookup-method, replaced-method). Such a subclass is the bean itself, not a proxy of it, and must not be wrapped in
 * the proxy of HotswapAgent, see #259.
 */
public class CglibSubclassingInstantiationStrategyTransformer {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(CglibSubclassingInstantiationStrategyTransformer.class);

    /**
     * Spring creates these beans in instantiateWithMethodInjection, with and without constructor arguments (the
     * same from Spring 3.2 to 7.0).
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy")
    public static void transform(CtClass clazz) throws CannotCompileException {
        CtMethod[] methods;
        try {
            methods = clazz.getDeclaredMethods("instantiateWithMethodInjection");
        } catch (NotFoundException e) {
            methods = new CtMethod[0];
        }
        for (CtMethod method : methods) {
            method.insertAfter(ProxyReplacer.class.getName() + ".registerMethodInjectionSubclass($_);");
        }
        if (methods.length == 0) {
            LOGGER.warning("The method instantiateWithMethodInjection was not found in the class '{}' of this Spring "
                    + "version. Beans with lookup or replaced methods are wrapped in the proxy of HotswapAgent, "
                    + "their final methods and fields see null.", clazz.getName());
        } else {
            LOGGER.debug("class '{}' patched to tell the beans with lookup or replaced methods.", clazz.getName());
        }
    }
}
