package org.hotswap.agent.plugin.javabeans;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * JavaBeans plugin - flush java.beans.Introspector caches
 *
 *  @author Vladimir Dvorak
 */
@Plugin(name = "JavaBeans",
        description = "",
        testedVersions = {"openjdk 1.7.0.95, 1.8.0_74"},
        expectedVersions = {"All between openjdk 1.7 - 1.8"}
        )
public class JavaBeansPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(JavaBeansPlugin.class);

    /**
     * Flag to check reload status. In unit test we need to wait for reload
     * finish before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag;

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public static void flushBeanIntrospectorsCaches(ClassLoader classLoader, CtClass ctClass) {
        try {
            LOGGER.debug("Flushing {}", ctClass.getName());

            Class<?> clazz = classLoader.loadClass(ctClass.getName());
            Class<?> threadGroupCtxClass = classLoader.loadClass("java.beans.ThreadGroupContext");
            Class<?> introspectorClass = classLoader.loadClass("java.beans.Introspector");

            Object declaredMethodCache = ReflectionHelper.get(null, introspectorClass, "declaredMethodCache");

            synchronized (declaredMethodCache) {
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

                LOGGER.trace("Removing class from declaredMethodCache.");
                ReflectionHelper.invoke(declaredMethodCache, declaredMethodCache.getClass(), "put",
                        new Class[] { Object.class, Object.class }, clazz, null);
            }
        } catch (Exception e) {
            LOGGER.error("classReload() exception {}.", e.getMessage());
        } finally {
            reloadFlag = false;
        }
    }
}
