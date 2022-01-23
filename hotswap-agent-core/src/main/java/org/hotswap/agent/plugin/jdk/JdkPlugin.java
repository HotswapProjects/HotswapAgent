/*
 * Copyright 2013-2022 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.jdk;

import java.util.Map;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * JdkPlugin plugin
 * <p>
 * Handle common stuff from jdk rt:
 * <ul>
 *  <li> flush java.beans.Introspector caches
 *  <li> flush ObjectStream caches
 * </ul>
 *  @author Vladimir Dvorak
 */
@Plugin(name = "JdkPlugin",
        description = "",
        testedVersions = {"openjdk 1.7.0.95, 1.8.0_74, 1.11.0_5"},
        expectedVersions = {"All between openjdk 1.7 - 1.11"}
        )
public class JdkPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(JdkPlugin.class);

    /**
     * Flag to check reload status. It is necessary (in unit tests)to wait for reload is finished before the test
     * can continue. Set flag to true in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag;

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic=false)
    public static void flushBeanIntrospectorCaches(ClassLoader classLoader, CtClass ctClass) {
        try {
            LOGGER.debug("Flushing {} from introspector", ctClass.getName());

            Class<?> clazz = classLoader.loadClass(ctClass.getName());
            Class<?> threadGroupCtxClass = classLoader.loadClass("java.beans.ThreadGroupContext");
            Class<?> introspectorClass = classLoader.loadClass("java.beans.Introspector");

            synchronized (classLoader) {
                Object contexts = ReflectionHelper.get(null, threadGroupCtxClass, "contexts");
                Object table[] = (Object[]) ReflectionHelper.get(contexts, "table");

                if (table != null) {
                    for (Object o: table) {
                        if (o != null) {
                            Object threadGroupContext = ReflectionHelper.get(o, "value");
                            if (threadGroupContext != null) {
                                LOGGER.trace("Removing from threadGroupContext");
                                ReflectionHelper.invoke(threadGroupContext, threadGroupCtxClass, "removeBeanInfo",
                                        new Class[] { Class.class }, clazz);
                            }
                        }
                    }
                }

                // java.beans.Introspector#declaredMethodCache was removed in j13
                // https://github.com/openjdk/jdk13u/commit/921b46738e0c3aaa2bf8c62e0accb0a5056190d3
                Object declaredMethodCache = ReflectionHelper.getNoException(null, introspectorClass, "declaredMethodCache");

                if (declaredMethodCache != null) {
                    LOGGER.info("Removing class from declaredMethodCache.");
                    ReflectionHelper.invoke(declaredMethodCache, declaredMethodCache.getClass(), "put",
                            new Class[] { Object.class, Object.class }, clazz, null);
                }
            }
        } catch (Exception e) {
            LOGGER.error("classReload() exception {}.", e.getMessage());
        } finally {
            reloadFlag = false;
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic=false)
    public static void flushIntrospectClassInfoCache(ClassLoader classLoader, CtClass ctClass) {
        // com.sun.beans.introspect.ClassInfo was intruduced in j9
        if (ClassFile.MAJOR_VERSION < ClassFile.JAVA_9) {
            return;
        }
        try {
            LOGGER.debug("Flushing {} from com.sun.beans.introspect.ClassInfo cache", ctClass.getName());

            Class<?> clazz = classLoader.loadClass(ctClass.getName());
            Class<?> classInfo = classLoader.loadClass("com.sun.beans.introspect.ClassInfo");

            Object cache = ReflectionHelper.get(null, classInfo, "CACHE");
            if (cache != null) {
                ReflectionHelper.invoke(cache, cache.getClass(), "clear", new Class[] { }, null);
            }
        } catch (Exception e) {
            LOGGER.error("flushClassInfoCache() exception {}.", e.getMessage());
        } finally {
            reloadFlag = false;
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic=false)
    public static void flushObjectStreamCaches(ClassLoader classLoader, CtClass ctClass) {
        try {
            LOGGER.debug("Flushing {} from ObjectStreamClass caches", ctClass.getName());

            Class<?> clazz = classLoader.loadClass(ctClass.getName());
            Class<?> objectStreamClassCache = classLoader.loadClass("java.io.ObjectStreamClass$Caches");

            Map localDescs = (Map) ReflectionHelper.get(null, objectStreamClassCache, "localDescs");

            if (localDescs != null) {
                localDescs.clear();
            }

            Map reflectors = (Map) ReflectionHelper.get(null, objectStreamClassCache, "reflectors");

            if (reflectors != null) {
                reflectors.clear();
            }

        } catch (Exception e) {
            LOGGER.error("classReload() exception {}.", e.getMessage());
        } finally {
            reloadFlag = false;
        }
    }

}
