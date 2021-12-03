package org.hotswap.agent.plugin.jackson;

import java.lang.reflect.Method;
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

    private final Set<Object> needToClearCacheObjects = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Command reloadJacksonCommand = new Command() {
        public void executeCommand() {
            reloadFlag = true;
            try {
                for (Object obj : needToClearCacheObjects) {
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

    private static void insertRegisterCacheObjectInConstructor(CtClass ctClass) throws NotFoundException, CannotCompileException{
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            StringBuilder src = new StringBuilder("{");
            src.append(PluginManagerInvoker.buildInitializePlugin(JacksonPlugin.class));
            src.append(PluginManagerInvoker.buildCallPluginMethod(JacksonPlugin.class, "registerNeedToClearCacheObjects",
                    "this", "java.lang.Object"));
            src.append("}");
            constructor.insertAfter(src.toString());
        }
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.ObjectMapper", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchObjectMapper(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", ctClass);
        insertRegisterCacheObjectInConstructor(ctClass);

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {_rootDeserializers.clear();}", ctClass);
        ctClass.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.ser.SerializerCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchSerializerCache(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", ctClass);
        insertRegisterCacheObjectInConstructor(ctClass);

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {flush();}", ctClass);
        ctClass.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.deser.DeserializerCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchDeserializerCache(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", ctClass);
        insertRegisterCacheObjectInConstructor(ctClass);

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {flushCachedDeserializers();}", ctClass);
        ctClass.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.ser.impl.ReadOnlyClassToSerializerMap", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchReadOnlyClassToSerializerMap(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", ctClass);
        insertRegisterCacheObjectInConstructor(ctClass);

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {_buckets = new com.fasterxml.jackson.databind.ser.impl.ReadOnlyClassToSerializerMap.Bucket[_size];}", ctClass);
        ctClass.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.type.TypeFactory", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchTypeFactory(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", ctClass);
        insertRegisterCacheObjectInConstructor(ctClass);

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() { _typeCache.clear();}", ctClass);
        ctClass.addMethod(clearCacheMethod);
    }

    @OnClassLoadEvent(classNameRegexp = "com.fasterxml.jackson.databind.util.LRUMap", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patch(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("Patch {}", ctClass);
        insertRegisterCacheObjectInConstructor(ctClass);

        CtMethod clearCacheMethod = CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() { clear();}", ctClass);
        ctClass.addMethod(clearCacheMethod);
    }

    public void registerNeedToClearCacheObjects(Object objectMapper) {
        needToClearCacheObjects.add(objectMapper);
        LOGGER.debug("register {}", objectMapper);
    }

    private static void invokeClearCacheMethod(Object obj) {
        try {
            LOGGER.debug("Reload {}", obj);
            ReflectionHelper.invoke(obj, CLEAR_CACHE_METHOD);
        } catch (Exception e) {
            LOGGER.error("Reload failed {}", obj.getClass(), e);
        }
    }
}
