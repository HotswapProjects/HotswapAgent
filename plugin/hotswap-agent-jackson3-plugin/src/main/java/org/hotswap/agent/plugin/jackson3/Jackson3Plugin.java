package org.hotswap.agent.plugin.jackson3;

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
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Clears the caches of Jackson 3 (tools.jackson), e.g. in Spring Boot 4, after a class is redefined, so that the
 * serializers and deserializers of the class are created again.
 * <p>
 * Like the JacksonPlugin for Jackson 2 (com.fasterxml.jackson), it registers the caches when they are created, and
 * the mapper only for its root deserializers: a reader or writer keeps the caches of its mapper also after the mapper
 * is garbage collected.
 */
@Plugin(name = "Jackson3Plugin",
        description = "Clear the caches of Jackson 3 after a class is redefined",
        testedVersions = {"3.0.1", "3.1.7", "3.2.0"},
        expectedVersions = {"3.x"}
)
public class Jackson3Plugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(Jackson3Plugin.class);

    private static final String CLEAR_CACHE_METHOD = "ha$$clearCache";

    /**
     * For the tests: set to true before a redefinition, it is false again when the caches are cleared the second time.
     */
    public static volatile boolean reloadFlag;

    private final Set<Object> caches = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    // one instance each, so that the classes redefined together clear the caches once
    private final Command clearCachesCommand = this::clearCachesAndRepeat;
    private final Command clearCachesAgainCommand = this::clearCachesAgain;

    @Init
    Scheduler scheduler;

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void reload() {
        // after the new class versions are installed, otherwise a request meanwhile may cache the old ones again
        scheduler.scheduleCommandOnClassesRedefinedOrTimeout(clearCachesCommand, 500);
    }

    @OnClassLoadEvent(classNameRegexp = "tools.jackson.databind.type.TypeFactory", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchTypeFactory(CtClass ctClass) throws CannotCompileException {
        patchCache(ctClass, "clearCache();");
    }

    // the default cache of the other caches, also of the root names and of the annotations marked with
    // @JacksonAnnotationsInside (LRUMap in Jackson 2)
    @OnClassLoadEvent(classNameRegexp = "tools.jackson.databind.util.SimpleLookupCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchSimpleLookupCache(CtClass ctClass) throws CannotCompileException {
        patchCache(ctClass, "clear();");
    }

    // also the read-only copy, which the serialization contexts look up first
    @OnClassLoadEvent(classNameRegexp = "tools.jackson.databind.ser.SerializerCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchSerializerCache(CtClass ctClass) throws CannotCompileException {
        patchCache(ctClass, "flush();");
    }

    // kept by the context of a JsonGenerator, a lookup in an empty bucket falls back to the SerializerCache
    @OnClassLoadEvent(classNameRegexp = "tools.jackson.databind.ser.impl.ReadOnlyClassToSerializerMap", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchReadOnlyClassToSerializerMap(CtClass ctClass) throws CannotCompileException {
        patchCache(ctClass, "java.util.Arrays.fill(_buckets, (Object) null);");
    }

    @OnClassLoadEvent(classNameRegexp = "tools.jackson.databind.deser.DeserializerCache", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchDeserializerCache(CtClass ctClass) throws CannotCompileException {
        patchCache(ctClass, "flushCachedDeserializers();");
    }

    // shared with the readers of the mapper
    @OnClassLoadEvent(classNameRegexp = "tools.jackson.databind.ObjectMapper", events = LoadEvent.DEFINE, skipSynthetic = false)
    public static void patchObjectMapper(CtClass ctClass) throws CannotCompileException {
        patchCache(ctClass, "_rootDeserializers.clear();");
    }

    private static void patchCache(CtClass ctClass, String clearCache) throws CannotCompileException {
        LOGGER.debug("Patch {}", ctClass.getName());
        String register = "{" + PluginManagerInvoker.buildInitializePlugin(Jackson3Plugin.class)
                + PluginManagerInvoker.buildCallPluginMethod(Jackson3Plugin.class, "registerCache",
                "this", "java.lang.Object")
                + "}";
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            // the others delegate to one of these
            if (constructor.callsSuper()) {
                constructor.insertAfter(register);
            }
        }
        ctClass.addMethod(CtNewMethod.make("public void " + CLEAR_CACHE_METHOD + "() {" + clearCache + "}", ctClass));
    }

    public void registerCache(Object cache) {
        caches.add(cache);
        // not the object, the constructor of a subclass has not run yet
        LOGGER.trace("Registered {}", cache.getClass().getName());
    }

    private void clearCachesAndRepeat() {
        clearCaches();
        // a request running meanwhile may have cached an old (de)serializer again
        scheduler.scheduleCommand(clearCachesAgainCommand, 1000);
    }

    private void clearCachesAgain() {
        clearCaches();
        reloadFlag = false;
    }

    private void clearCaches() {
        Object[] registered = caches.toArray();
        for (Object cache : registered) {
            try {
                ReflectionHelper.invoke(cache, CLEAR_CACHE_METHOD);
            } catch (Exception e) {
                LOGGER.error("Failed to clear {}", e, cache.getClass().getName());
            }
        }
        LOGGER.debug("Cleared {} Jackson 3 caches", registered.length);
    }
}
