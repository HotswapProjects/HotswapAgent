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
package org.hotswap.agent.util;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.handler.AnnotationProcessor;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

/**
 * @author Jiri Bubnik
 */
@Deprecated
public class AppClassLoaderExecutor {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AnnotationProcessor.class);

    ClassLoader appClassLoader;
    ProtectionDomain protectionDomain;

    public AppClassLoaderExecutor(ClassLoader appClassLoader, ProtectionDomain protectionDomain) {
        this.appClassLoader = appClassLoader;
        this.protectionDomain = protectionDomain;
    }

    public Object execute(String className, String method, Object... params)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        LOGGER.error("Start");
        PluginManager.getInstance().initClassLoader(appClassLoader, protectionDomain);

        Class classInAppClassLoader = Class.forName(className, true, appClassLoader);

        LOGGER.error("Executing: requestedClassLoader={}, resolvedClassLoader={}, class={}, method={}, params={}",
                appClassLoader, classInAppClassLoader.getClassLoader(), classInAppClassLoader, method, params);

        Class[] paramTypes = new Class[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null)
                throw new IllegalArgumentException("Cannot execute for null parameter classNameRegexp");
            else if (!isSimpleType(params[i].getClass())) {
                throw new IllegalArgumentException("Use only simple parameter values.");
            } else {
                paramTypes[i] = params[i].getClass();
            }
        }

        Object instance = classInAppClassLoader.newInstance();
        Method m = classInAppClassLoader.getDeclaredMethod(method, paramTypes);

        Thread.currentThread().setContextClassLoader(appClassLoader);
        return m.invoke(instance, params);
    }

    private boolean isSimpleType(Class<? extends Object> aClass) {
        // primitive data types has null class loader
        return aClass.getClassLoader() == null;
    }
}
