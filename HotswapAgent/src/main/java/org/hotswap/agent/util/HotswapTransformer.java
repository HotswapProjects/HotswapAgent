package org.hotswap.agent.util;

import org.hotswap.agent.logging.AgentLogger;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;

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

    protected Map<Pattern, List<ClassFileTransformer>> registeredTransformers = new HashMap<Pattern, List<ClassFileTransformer>>();

    // keep track about which classloader requested which transformer
    protected Map<ClassFileTransformer, ClassLoader> classLoaderTransformers = new HashMap<ClassFileTransformer, ClassLoader>();


    /**
     * Register a transformer for a regexp matching class names.
     * Used by {@link org.hotswap.agent.annotation.Transform} annotation respective
     * {@link org.hotswap.agent.annotation.handler.TransformHandler}.
     *
     * @param classLoader the classloader to which this transformation is associated
     * @param classNameRegexp regexp to match fully qualified class name.
     *                        Because "." is any character in regexp, this will match / in the transform method as well
     *                        (diffentence between java/lang/String and java.lang.String).
     * @param transformer     the transformer to be called for each class matching regexp.
     */
    public void registerTransformer(ClassLoader classLoader, String classNameRegexp, ClassFileTransformer transformer) {
        LOGGER.debug("Registering transformer for class regexp '{}'.", classNameRegexp);

        Pattern pattern = Pattern.compile(normalizeTypeRegexp(classNameRegexp));

        // register pattern
        List<ClassFileTransformer> transformerList = registeredTransformers.get(pattern);
        if (transformerList == null) {
            transformerList = new LinkedList<ClassFileTransformer>();
            registeredTransformers.put(pattern, transformerList);
        }
        transformerList.add(transformer);

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
        Pattern pattern = Pattern.compile(normalizeTypeRegexp(classNameRegexp));

        List<ClassFileTransformer> transformerList = registeredTransformers.get(pattern);
        if (transformerList != null) {
            transformerList.remove(transformer);
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
                for (List<ClassFileTransformer> transformerList : registeredTransformers.values())
                    transformerList.remove(entry.getKey());
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
    public byte[] transform(ClassLoader classLoader, String className, Class<?> redefiningClass,
                            ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
        LOGGER.trace("Transform on class '{}' @{} redefiningClass '{}'.", className, classLoader, redefiningClass);

        byte[] result = bytes;
        try {
            // call transform on all registered transformers
            for (Pattern pattern : new LinkedList<Pattern>(registeredTransformers.keySet())) {
                if ((className != null && pattern.matcher(className).matches()) ||
                        (redefiningClass != null && pattern.matcher(redefiningClass.getName()).matches())) {
                    for (ClassFileTransformer transformer : new LinkedList<ClassFileTransformer>(registeredTransformers.get(pattern))) {
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
