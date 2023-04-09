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
package org.hotswap.agent.plugin.glassfish;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class WebappClassLoaderTransformer {

    protected static AgentLogger LOGGER = AgentLogger.getLogger(WebappClassLoaderTransformer.class);

    private static boolean webappClassLoaderPatched = false;

    @OnClassLoadEvent(classNameRegexp = "org.glassfish.web.loader.WebappClassLoader")
    public static void patchWebappClassLoader(ClassPool classPool,CtClass ctClass) throws CannotCompileException, NotFoundException {
        if (!webappClassLoaderPatched) {
            try {
                // clear classloader cache
                ctClass.getDeclaredMethod("getResource", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                        "resourceEntries.clear();"
                );
                ctClass.getDeclaredMethod("getResourceAsStream", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                        "resourceEntries.clear();"
                );
                webappClassLoaderPatched = true;
            } catch (NotFoundException e) {
                LOGGER.trace("WebappClassLoader does not contain getResource(), getResourceAsStream method.");
            }
        }
    }

}
