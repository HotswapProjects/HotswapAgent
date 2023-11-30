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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import org.hotswap.agent.annotation.handler.PluginClassFileTransformer;
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
    private static final Set<String> skippedClassLoaders = new HashSet<>(Arrays.asList(
            "jdk.internal.reflect.DelegatingClassLoader",
            "sun.reflect.DelegatingClassLoader"
    ));

    // TODO : check if felix class loaders could be skipped
    private static final Set<String> excludedClassLoaders = new HashSet<>(Arrays.asList(
            "org.apache.felix.framework.BundleWiringImpl$BundleClassLoader", // delegating ClassLoader in GlassFish
            "org.apache.felix.framework.BundleWiringImpl$BundleClassLoaderJava5" // delegating ClassLoader in_GlassFish
    ));

    private static class RegisteredTransformersRecord {
        Pattern pattern;
        List<HaClassFileTransformer> transformerList = new LinkedList<>();
    }

    protected Map<String, RegisteredTransformersRecord> redefinitionTransformers = new LinkedHashMap<>();
    protected Map<String, RegisteredTransformersRecord> otherTransformers = new LinkedHashMap<>();

    // keep track about which classloader requested which transformer
    protected Map<ClassFileTransformer, ClassLoader> classLoaderTransformers = new LinkedHashMap<>();

    protected Map<ClassLoader, Object> seenClassLoaders = new WeakHashMap<>();
    private List<Pattern> includedClassLoaderPatterns;
    private List<Pattern> excludedClassLoaderPatterns;
    public List<Pattern> getIncludedClassLoaderPatterns() {
        return includedClassLoaderPatterns;
    }

    public void setIncludedClassLoaderPatterns(List<Pattern> includedClassLoaderPatterns) {
        this.includedClassLoaderPatterns = includedClassLoaderPatterns;
    }


    /**
     * @param excludedClassLoaderPatterns
     *            the excludedClassLoaderPatterns to set
     */
    public void setExcludedClassLoaderPatterns(List<Pattern> excludedClassLoaderPatterns) {
        this.excludedClassLoaderPatterns = excludedClassLoaderPatterns;
    }

    public List<Pattern> getExcludedClassLoaderPatterns() {
        return excludedClassLoaderPatterns;
    }

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
    public void registerTransformer(ClassLoader classLoader, String classNameRegexp, HaClassFileTransformer transformer) {
        LOGGER.debug("Registering transformer for class regexp '{}'.", classNameRegexp);

        String normalizeRegexp = normalizeTypeRegexp(classNameRegexp);

        Map<String, RegisteredTransformersRecord> transformersMap = getTransformerMap(transformer);

        RegisteredTransformersRecord transformerRecord = transformersMap.get(normalizeRegexp);
        if (transformerRecord == null) {
            transformerRecord = new RegisteredTransformersRecord();
            transformerRecord.pattern = Pattern.compile(normalizeRegexp);
            transformersMap.put(normalizeRegexp, transformerRecord);
        }

        if (!transformerRecord.transformerList.contains(transformer)) {
            transformerRecord.transformerList.add(transformer);
        }

        // register classloader association to allow classloader unregistration
        if (classLoader != null) {
            classLoaderTransformers.put(transformer, classLoader);
        }
    }

    private Map<String, RegisteredTransformersRecord> getTransformerMap(HaClassFileTransformer transformer) {
        if (transformer.isForRedefinitionOnly()) {
            return redefinitionTransformers;
        }
        return otherTransformers;
    }

    /**
     * Remove registered transformer.
     *
     * @param classNameRegexp regexp to match fully qualified class name.
     * @param transformer     currently registered transformer
     */
    public void removeTransformer(String classNameRegexp, HaClassFileTransformer transformer) {
        String normalizeRegexp = normalizeTypeRegexp(classNameRegexp);
        Map<String, RegisteredTransformersRecord> transformersMap = getTransformerMap(transformer);
        RegisteredTransformersRecord transformerRecord = transformersMap.get(normalizeRegexp);
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
                for (RegisteredTransformersRecord transformerRecord : redefinitionTransformers.values()) {
                    transformerRecord.transformerList.remove(entry.getKey());
                }
                for (RegisteredTransformersRecord transformerRecord : otherTransformers.values()) {
                    transformerRecord.transformerList.remove(entry.getKey());
                }
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

        // Skip delegating classloaders used for reflection
        String classLoaderClassName = classLoader != null ? classLoader.getClass().getName() : null;
        if (skippedClassLoaders.contains(classLoaderClassName)) {
            return bytes;
        }

        LOGGER.trace("Transform on class '{}' @{} redefiningClass '{}'.", className, classLoader, redefiningClass);

        List<ClassFileTransformer> toApply = new ArrayList<>();
        List<PluginClassFileTransformer> pluginTransformers = new ArrayList<>();
        try {
            // 1. call transform method of defining transformers
            for (RegisteredTransformersRecord transformerRecord : new ArrayList<RegisteredTransformersRecord>(otherTransformers.values())) {
                if ((className != null && transformerRecord.pattern.matcher(className).matches()) ||
                        (redefiningClass != null && transformerRecord.pattern.matcher(redefiningClass.getName()).matches())) {
                    for (ClassFileTransformer transformer : new ArrayList<ClassFileTransformer>(transformerRecord.transformerList)) {
                        if(transformer instanceof PluginClassFileTransformer) {
                            PluginClassFileTransformer pcft = PluginClassFileTransformer.class.cast(transformer);
                            if(!pcft.isPluginDisabled(classLoader)) {
                                pluginTransformers.add(pcft);
                            }
                        } else {
                            toApply.add(transformer);
                        }
                    }
                }
            }
            // 2. call transform method of redefining transformers
            if (redefiningClass != null && className != null) {
                for (RegisteredTransformersRecord transformerRecord : new ArrayList<RegisteredTransformersRecord>(redefinitionTransformers.values())) {
                    if (transformerRecord.pattern.matcher(className).matches()) {
                        for (ClassFileTransformer transformer : new ArrayList<ClassFileTransformer>(transformerRecord.transformerList)) {
                            if(transformer instanceof PluginClassFileTransformer) {
                                PluginClassFileTransformer pcft = PluginClassFileTransformer.class.cast(transformer);
                                if(!pcft.isPluginDisabled(classLoader)) {
                                    pluginTransformers.add(pcft);
                                }
                            } else {
                                toApply.add(transformer);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error transforming class '" + className + "'.", t);
        }

        if(!pluginTransformers.isEmpty()) {
            pluginTransformers =  reduce(classLoader, pluginTransformers, className);
        }

        // ensure classloader initialized
       ensureClassLoaderInitialized(classLoader, protectionDomain);

        if(toApply.isEmpty() && pluginTransformers.isEmpty()) {
            LOGGER.trace("No transformers defing for {} ", className);
            return bytes;
        }

       try {
           byte[] result = bytes;

           for(ClassFileTransformer transformer: pluginTransformers) {
               LOGGER.trace("Transforming class '" + className + "' with transformer '" + transformer + "' " + "@ClassLoader" + classLoader + ".");
               result = transformer.transform(classLoader, className, redefiningClass, protectionDomain, result);
           }

           for(ClassFileTransformer transformer: toApply) {
               LOGGER.trace("Transforming class '" + className + "' with transformer '" + transformer + "' " + "@ClassLoader" + classLoader + ".");
               result = transformer.transform(classLoader, className, redefiningClass, protectionDomain, result);
           }
           return result;
       } catch (Throwable t) {
           LOGGER.error("Error transforming class '" + className + "'.", t);
       }
       return bytes;
    }

    LinkedList<PluginClassFileTransformer> reduce(final ClassLoader classLoader, List<PluginClassFileTransformer> pluginCalls, String className) {
        LinkedList<PluginClassFileTransformer> reduced = new LinkedList<>();

        Map<String, PluginClassFileTransformer> fallbackMap = new HashMap<>();

        for (PluginClassFileTransformer pcft : pluginCalls) {
            try {
                String pluginGroup = pcft.getPluginGroup();
                if(pcft.versionMatches(classLoader)){
                    if (pluginGroup != null) {
                        fallbackMap.put(pluginGroup, null);
                    }
                    reduced.add(pcft);
                } else if(pcft.isFallbackPlugin()){
                    if (pluginGroup != null && !fallbackMap.containsKey(pluginGroup)) {
                        fallbackMap.put(pluginGroup, pcft);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Error evaluating aplicability of plugin", e);
            }
        }

        for (PluginClassFileTransformer pcft: fallbackMap.values()) {
            if (pcft != null) {
                reduced.add(pcft);
            }
        }

        return reduced;
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
                if (shouldScheduleClassLoader(classLoader)) {
                    PluginManager.getInstance().initClassLoader(classLoader, protectionDomain);
                }
            }
        }
    }

    private boolean shouldScheduleClassLoader(final ClassLoader classLoader) {
        String name = classLoader.getClass().getName();
        if (excludedClassLoaders.contains(name)) {
            return false;
        }

        if (includedClassLoaderPatterns != null) {
            for (Pattern pattern : includedClassLoaderPatterns) {
                if (pattern.matcher(name).matches()) {
                    return true;
                }
            }
            return false;
        }

        if (excludedClassLoaderPatterns != null) {
            for (Pattern pattern : excludedClassLoaderPatterns) {
                if (pattern.matcher(name).matches()) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Transform type to ^regexp$ form - match only whole pattern.
     *
     * @param registeredType type
     * @return
     */
    protected String normalizeTypeRegexp(String registeredType) {
        String regexp = registeredType;
        if (!registeredType.startsWith("^")){
            regexp = "^" + regexp;
        }
        if (!registeredType.endsWith("$")){
            regexp = regexp + "$";
        }

        return regexp;
    }

}
