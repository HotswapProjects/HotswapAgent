package org.hotswap.agent.util;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Java instrumentation transformer.
 * <p/>
 * The is the single instance of transformer registered by HotswapAgent. It will delegate to plugins to
 * do the transformer work.
 *
 * @author Jiri Bubnik
 */
public class HotswapTransformer implements ClassFileTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapTransformer.class);

    /**
     * Exclude these classLoaders from initialization (system classloaders). Note that
     */
    private static final Set<String> excludedClassLoaders = new HashSet<String>(Arrays.asList(
            "sun.reflect.DelegatingClassLoader"
    ));

    private static class RegisteredTransformersRecord {
        Pattern pattern;
        List<ClassFileTransformer> transformerList = new LinkedList<ClassFileTransformer>();
    }

    protected Map<String, RegisteredTransformersRecord> registeredTransformers = new HashMap<String, RegisteredTransformersRecord>();

    // keep track about which classloader requested which transformer
    protected Map<ClassFileTransformer, ClassLoader> classLoaderTransformers = new HashMap<ClassFileTransformer, ClassLoader>();

    protected Map<ClassLoader, Object> seenClassLoaders = new WeakHashMap<ClassLoader, Object>();

    /**
     * Register a transformer for a regexp matching class names.
     * Used by {@link org.hotswap.agent.annotation.OnClassLoadEvent} annotation respective
     * {@link org.hotswap.agent.annotation.handler.OnClassLoadedHandler}.
     *
     * @param classLoader the classloader to which this transformation is associated
     * @param classNameRegexp regexp to match fully qualified class name.
     *                        Because "." is any character in regexp, this will match / in the transform method as well
     *                        (diffentence between java/lang/String and java.lang.String).
     * @param transformer     the transformer to be called for each class matching regexp.
     */
    public void registerTransformer(ClassLoader classLoader, String classNameRegexp, ClassFileTransformer transformer) {
        LOGGER.debug("Registering transformer for class regexp '{}'.", classNameRegexp);

        String normalizeRegexp = normalizeTypeRegexp(classNameRegexp);
        RegisteredTransformersRecord transformerRecord = registeredTransformers.get(normalizeRegexp);
        if (transformerRecord == null) {
            transformerRecord = new RegisteredTransformersRecord();
            transformerRecord.pattern = Pattern.compile(normalizeRegexp);
            registeredTransformers.put(normalizeRegexp, transformerRecord);
        }
        transformerRecord.transformerList.add(transformer);

        // register classloader association to allow classloader unregistration
        if (classLoader != null) {
            classLoaderTransformers.put(transformer, classLoader);
        }
    }

    /**
     * Remove registered transformer.
     *
     * @param classNameRegexp regexp to match fully qualified class name.
     * @param transformer     currently registered transformer
     */
    public void removeTransformer(String classNameRegexp, ClassFileTransformer transformer) {
        String normalizeRegexp = normalizeTypeRegexp(classNameRegexp);
        RegisteredTransformersRecord transformerRecord = registeredTransformers.get(normalizeRegexp);
        if (transformerRecord != null) {
            transformerRecord.transformerList.remove(transformer);
        }
    }

    /**
     * Remove all transformers registered with a classloader
     * @param classLoader
     */
    public void closeClassLoader(ClassLoader classLoader) {
        for (Iterator<Map.Entry<ClassFileTransformer, ClassLoader>> entryIterator = classLoaderTransformers.entrySet().iterator();
                entryIterator.hasNext(); ) {
            Map.Entry<ClassFileTransformer, ClassLoader> entry = entryIterator.next();
            if (entry.getValue().equals(classLoader)) {
                entryIterator.remove();
                for (RegisteredTransformersRecord transformerRecord : registeredTransformers.values())
                    transformerRecord.transformerList.remove(entry.getKey());
            }
        }

        LOGGER.debug("All transformers removed for classLoader {}", classLoader);
    }

    /**
     * Main transform method called by Java instrumentation.
     * <p/>
     * <p>It does not do the instrumentation itself, instead iterates registered transformers and compares
     * registration class regexp - if the regexp matches, the classloader is called.
     * <p/>
     * <p>Note that class bytes may be send to multiple transformers, but the order is not defined.
     *
     * @see ClassFileTransformer#transform(ClassLoader, String, Class, java.security.ProtectionDomain, byte[])
     */
    @Override
    public byte[] transform(final ClassLoader classLoader, String className, Class<?> redefiningClass,
                            final ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
        LOGGER.trace("Transform on class '{}' @{} redefiningClass '{}'.", className, classLoader, redefiningClass);

        // ensure classloader initialized
       ensureClassLoaderInitialized(classLoader, protectionDomain);

        byte[] result = bytes;
        try {
            // call transform on all registered transformers
            for (RegisteredTransformersRecord transformerRecord : new LinkedList<RegisteredTransformersRecord>(registeredTransformers.values())) {
                if ((className != null && transformerRecord.pattern.matcher(className).matches()) ||
                        (redefiningClass != null && transformerRecord.pattern.matcher(redefiningClass.getName()).matches())) {
                    for (ClassFileTransformer transformer : new LinkedList<ClassFileTransformer>(transformerRecord.transformerList)) {
                        LOGGER.trace("Transforming class '" + className +
                                "' with transformer '" + transformer + "' " + "@ClassLoader" + classLoader + ".");
                        result = transformer.transform(classLoader, className, redefiningClass, protectionDomain, result);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error transforming class '" + className + "'.", t);
        }



        return result;
    }

    /**
     * Every classloader should be initialized. Usually if anything interesting happens,
     * it is initialized during plugin initialization process. However, some plugins (e.g. Hotswapper)
     * are triggered during classloader initialization process itself (@Init on static method). In this case,
     * the plugin will be never invoked, until the classloader initialization is invoked from here.
     *
     * Schedule with some timeout to allow standard plugin initialization process to precede.
     *
     * @param classLoader the classloader to which this transformation is associated
     * @param protectionDomain associated protection domain (if any)
     */
    protected void ensureClassLoaderInitialized(final ClassLoader classLoader, final ProtectionDomain protectionDomain) {
        if (!seenClassLoaders.containsKey(classLoader)) {
            seenClassLoaders.put(classLoader, null);

            if (classLoader == null) {
                // directly init null (bootstrap) classloader
                PluginManager.getInstance().initClassLoader(null, protectionDomain);
            } else {
                // ensure the classloader should not be excluded
                if (!excludedClassLoaders.contains(classLoader.getClass().getName())) {
                    // schedule the excecution
                    PluginManager.getInstance().getScheduler().scheduleCommand(new Command() {
                        @Override
                        public void executeCommand() {
                            PluginManager.getInstance().initClassLoader(classLoader, protectionDomain);
                        }

                        @Override
                        public String toString() {
                            return "executeCommand: initClassLoader(" + classLoader + ")";
                        }
                    }, 1000);
                }
            }
        }
    }


    /**
     * Transform type to ^regexp$ form - match only whole pattern.
     *
     * @param registeredType type
     * @return
     */
    protected String normalizeTypeRegexp(String registeredType) {
        String regexp = registeredType;
        if (!registeredType.startsWith("^"))
            regexp = "^" + regexp;
        if (!registeredType.endsWith("$"))
            regexp = regexp + "$";

        return regexp;
    }

}
