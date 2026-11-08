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
package org.hotswap.agent.plugin.jdk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

                ReflectionHelper.invoke(null, introspectorClass, "flushFromCaches",
                    new Class[] { Class.class }, clazz);
            }
            flushClassInfoCache(clazz);
        } catch (Exception e) {
            LOGGER.error("flushBeanIntrospectorCaches() exception {}.", e.getMessage());
        } finally {
            reloadFlag = false;
        }
    }

    // Introspector.flushFromCaches() removes the class from com.sun.beans.introspect.ClassInfo only since Java 17,
    // Java 8 has no ClassInfo
    private static final boolean FLUSH_CLASS_INFO_CACHE = isJava9To16();

    // resolved once, on the first redefinition
    private static boolean classInfoCacheResolved;
    private static Object classInfoCache;
    private static Method classInfoCacheRemove;

    private static boolean isJava9To16() {
        try {
            int version = Integer.parseInt(System.getProperty("java.specification.version"));
            return version >= 9 && version <= 16;
        } catch (NumberFormatException e) {
            return false; // 1.8
        }
    }

    /**
     * Introspector builds bean info from com.sun.beans.introspect.ClassInfo (Java 9+), which caches the methods of
     * the class. Introspector.flushFromCaches() removes the class from it only since Java 17, so on Java 9 - 16
     * a new getter or setter was not visible to introspection (e.g. EL property not found).
     */
    private static synchronized void flushClassInfoCache(Class<?> clazz) {
        if (!FLUSH_CLASS_INFO_CACHE) {
            return;
        }
        if (!classInfoCacheResolved) {
            classInfoCacheResolved = true;
            try {
                Field cacheField = Class.forName("com.sun.beans.introspect.ClassInfo").getDeclaredField("CACHE");
                cacheField.setAccessible(true);
                Object cache = cacheField.get(null);
                Method remove = findMethod(cache.getClass(), "remove", Object.class);
                remove.setAccessible(true);
                classInfoCache = cache;
                classInfoCacheRemove = remove;
            } catch (Exception e) {
                LOGGER.warning("Unable to flush com.sun.beans.introspect.ClassInfo cache, new bean properties may not be " +
                        "visible until restart. Add --add-opens java.desktop/com.sun.beans.introspect=ALL-UNNAMED " +
                        "--add-opens java.desktop/com.sun.beans.util=ALL-UNNAMED. {}", e.getMessage());
            }
        }
        if (classInfoCacheRemove != null) {
            try {
                classInfoCacheRemove.invoke(classInfoCache, clazz);
            } catch (Exception e) {
                LOGGER.debug("Unable to flush com.sun.beans.introspect.ClassInfo cache of {}", e, clazz.getName());
            }
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                // try superclass
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic=false)
    public static void flushObjectStreamCaches(ClassLoader classLoader, CtClass ctClass) {
        Class<?> clazz;
        Object localDescs;
        Object reflectors;
        try {
            LOGGER.debug("Flushing {} from ObjectStreamClass caches", ctClass.getName());

            clazz = classLoader.loadClass(ctClass.getName());
            Class<?> objectStreamClassCache = classLoader.loadClass("java.io.ObjectStreamClass$Caches");

            localDescs = ReflectionHelper.get(null, objectStreamClassCache, "localDescs");
            reflectors = ReflectionHelper.get(null, objectStreamClassCache, "reflectors");

        } catch (Exception e) {
            LOGGER.error("flushObjectStreamCaches() java.io.ObjectStreamClass$Caches not found.", e.getMessage());
            return;
        }

        boolean java17;

        try {
            ((Map) localDescs).clear();
            ((Map) reflectors).clear();
            java17 = false;
        } catch (Exception e) {
            java17 = true;
        }

        if (java17) {
            try {
                Object localDescsMap = ReflectionHelper.get(localDescs, "map");
                ReflectionHelper.invoke(localDescsMap, localDescsMap.getClass(), "remove", new Class[] { Class.class }, clazz);
                Object reflectorsMap = ReflectionHelper.get(reflectors, "map");
                ReflectionHelper.invoke(reflectorsMap, reflectorsMap.getClass(), "remove", new Class[] { Class.class }, clazz);
            } catch (Exception e) {
                LOGGER.error("flushObjectStreamCaches() exception {}.", e.getMessage());
            }
        }
    }

}
