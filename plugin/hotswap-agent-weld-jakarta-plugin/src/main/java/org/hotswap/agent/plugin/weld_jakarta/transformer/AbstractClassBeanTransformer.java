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
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;

public class AbstractClassBeanTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(AbstractClassBeanTransformer.class);

    /**
     *
     * @param ctClass
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bean.AbstractClassBean")
    public static void transformAbstractClassBean(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (!HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        CtMethod method = ctClass.getDeclaredMethod("cleanupAfterBoot");
        method.setBody("{ }");
        LOGGER.debug("AbstractClassBean.cleanupAfterBoot patched");
    }
}
