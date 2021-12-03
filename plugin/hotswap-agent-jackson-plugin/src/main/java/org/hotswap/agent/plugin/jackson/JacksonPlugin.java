package org.hotswap.agent.plugin.jackson;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Reload jackson caches after class change
 *
 * @author liuzhengyang
 * 2021/12/3
 */
@Plugin(name = "JacksonPlugin",
        description = "Reload jackson caches after class change",
        testedVersions = {"2.13.0"},
        expectedVersions = {"2.13.0"}
)
public class JacksonPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JacksonPlugin.class);

    private static final String CLEAR_CACHE_METHOD = "ha$$clearCache";
    public static boolean reloadFlag = false;

    private final Set<Object> objectMappers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<Object> serializerCaches = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<Object> deserializerCaches = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Command reloadJacksonCommand = new Command() {
        public void executeCommand() {
            reloadFlag = true;
            try {
                for (Object obj : objectMappers) {
                    invokeClearCacheMethod(obj);
                }
                for (Object obj : serializerCaches) {
                    invokeClearCacheMethod(obj);
                }
                for (Object obj : deserializerCaches) {
                    invokeClearCacheMethod(obj);
                }
                LOGGER.info("Reloaded Jackson.");
                reloadFlag = false;
            } catch (Exception e) {
                LOGGER.error("Error reloading Jackson.", e);
            }
        }
    };

    @Init
    private Scheduler scheduler;

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void reload() {
        scheduler.scheduleCommand(reloadJacksonCommand, 500);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.ObjectMapper", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchObjectMapper(CtClass clazz) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", clazz);
        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            StringBuilder src = new StringBuilder("{");
            src.append(PluginManagerInvoker.buildInitializePlugin(JacksonPlugin.class));
            src.append(PluginManagerInvoker.buildCallPluginMethod(JacksonPlugin.class, "registerObjectMappers",
                    "this", "java.lang.Object"));
            src.append("}");
            constructor.insertAfter(src.toString());
        }

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {_rootDeserializers.clear();}", clazz);
        clazz.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.ser.SerializerCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchSerializerCache(CtClass clazz) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", clazz);
        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            StringBuilder src = new StringBuilder("{");
            src.append(PluginManagerInvoker.buildInitializePlugin(JacksonPlugin.class));
            src.append(PluginManagerInvoker.buildCallPluginMethod(JacksonPlugin.class, "registerSerializerCaches",
                    "this", "java.lang.Object"));
            src.append("}");
            constructor.insertAfter(src.toString());
        }

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {flush();}", clazz);
        clazz.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.deser.DeserializerCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchDeserializerCache(CtClass clazz) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", clazz);
        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            StringBuilder src = new StringBuilder("{");
            src.append(PluginManagerInvoker.buildInitializePlugin(JacksonPlugin.class));
            src.append(PluginManagerInvoker.buildCallPluginMethod(JacksonPlugin.class, "registerDeserializerCaches",
                    "this", "java.lang.Object"));
            src.append("}");
            constructor.insertAfter(src.toString());
        }

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {flushCachedDeserializers();}", clazz);
        clazz.addMethod(clearCacheMethod);
    }

    public void registerObjectMappers(Object objectMapper) {
        objectMappers.add(objectMapper);
        LOGGER.debug("register {}", objectMapper);
    }

    public void registerSerializerCaches(Object obj) {
        serializerCaches.add(obj);
        LOGGER.debug("register {}", obj);
    }

    public void registerDeserializerCaches(Object obj) {
        deserializerCaches.add(obj);
        LOGGER.debug("register {}", obj);
    }

    private void invokeClearCacheMethod(Object o) {
        try {
            ReflectionHelper.invoke(o, CLEAR_CACHE_METHOD);
        } catch (Exception e) {
            LOGGER.error("Reload failed {}", o.getClass(), e);
        }
    }
}
