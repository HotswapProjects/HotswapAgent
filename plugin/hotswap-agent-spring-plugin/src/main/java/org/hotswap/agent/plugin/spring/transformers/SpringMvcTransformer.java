/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

public class SpringMvcTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringMvcTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.core.LocalVariableTableParameterNameDiscoverer")
    public static void replaceLocalVariableTableParameterNameDiscoverer(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtConstructor[] constructors = ctClass.getConstructors();
        for (CtConstructor ctConstructor : constructors) {
            ctConstructor.insertAfter(" {org.hotswap.agent.plugin.spring.core.ResetRequestMappingCaches.setLocalParameterNameDiscovers(this);} ");
        }

        LOGGER.debug("LocalVariableTableParameterNameDiscoverer patched");
    }
}