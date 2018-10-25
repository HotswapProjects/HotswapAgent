package org.hotswap.agent.plugin.proxy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.hscglib.CglibEnhancerProxyTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.GeneratorParams;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatcherFactory;

/**
 * Redefines proxy classes that implement or extend changed interfaces or classes. Currently it supports proxies created
 * with Java reflection and the Cglib library.
 *
 * @author Erki Ehtla, Vladimir Dvorak
 *
 */
@Plugin(name = "Proxy", description = "Redefines proxies", testedVersions = { "" }, expectedVersions = { "all" }, supportClass = RedefinitionScheduler.class)
public class ProxyPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyPlugin.class);
    static boolean isJava8OrNewer = WatcherFactory.JAVA_VERSION >= 18;

    /**
     * Flag to check reload status. In unit test we need to wait for reload
     * finish before the test can continue. Set flag to true in the test class
     * and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    private static Set<String> proxyRedefiningMap = new HashSet<>();

    @OnClassLoadEvent(classNameRegexp = "com.sun.proxy.\\$Proxy.*", events = LoadEvent.REDEFINE, skipSynthetic = false)
    public static void transformJavaProxy(final Class<?> classBeingRedefined, final ClassLoader classLoader) {

    /*
     * Proxy can't be redefined directly in this method (and return new proxy class bytes), since the classLoader contains
     * OLD definition of proxie's interface. Therefore proxy is defined in deferred command (after some delay)
     * after proxied interface is redefined in DCEVM.
     */
        if (!ClassLoaderHelper.isClassLoderStarted(classLoader)) {
            return;
        }

        final String className = classBeingRedefined.getName();

        if (proxyRedefiningMap.contains(className)) {
            proxyRedefiningMap.remove(className);
            return;
        }

        proxyRedefiningMap.add(className);

        final Map<String, String> signatureMapOrig = ProxyClassSignatureHelper.getNonSyntheticSignatureMap(classBeingRedefined);

        reloadFlag = true;

        // TODO: can be single command if scheduler guarantees the keeping execution order in the order of redefinition
        PluginManager.getInstance().getScheduler().scheduleCommand(new ReloadJavaProxyCommand(classLoader, className, signatureMapOrig), 50);
    }

//    @OnClassLoadEvent(classNameRegexp = "com/sun/proxy/\\$Proxy.*", events = LoadEvent.REDEFINE, skipSynthetic = false)
//    public static byte[] transformJavaProxy(final Class<?> classBeingRedefined, final byte[] classfileBuffer,
//            final ClassPool cp, final CtClass cc) throws IllegalClassFormatException, IOException, RuntimeException {
//        try {
//            return JavassistProxyTransformer.transform(classBeingRedefined, classfileBuffer, cc, cp);
//        } catch (Exception e) {
//            LOGGER.error("Error redifining Cglib proxy {}", e, classBeingRedefined.getName());
//        }
//        return classfileBuffer;
//    }

    // alternative method of redefining Java proxies, uses a new classlaoder instance
    // @OnClassLoadEvent(classNameRegexp = "com/sun/proxy/\\$Proxy.*", events = LoadEvent.REDEFINE, skipSynthetic =
    // false)
    // public static byte[] transformJavaProxy(final Class<?> classBeingRedefined, final byte[] classfileBuffer,
    // final ClassLoader loader) throws IllegalClassFormatException, IOException, RuntimeException {
    // try {
    // return NewClassLoaderJavaProxyTransformer.transform(classBeingRedefined, classfileBuffer, loader);
    // } catch (Exception e) {
    // LOGGER.error("Error redifining Cglib proxy {}", e, classBeingRedefined.getName());
    // }
    // return classfileBuffer;
    // }
    //
    // // alternative method of redefining Java proxies, uses a 2 step process. Crashed with jvm8
    // @OnClassLoadEvent(classNameRegexp = "com/sun/proxy/\\$Proxy.*", events = LoadEvent.REDEFINE, skipSynthetic =
    // false)
    // public static byte[] transformJavaProxy(final Class<?> classBeingRedefined, final byte[] classfileBuffer,
    // final ClassPool cp) {
    // try {
    // return JavaProxyTransformer.transform(classBeingRedefined, cp, classfileBuffer);
    // } catch (Exception e) {
    // LOGGER.error("Error redifining Cglib proxy {}", e, classBeingRedefined.getName());
    // }
    // return classfileBuffer;
    // }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
    public static byte[] transformCglibProxy(final Class<?> classBeingRedefined, final byte[] classfileBuffer,
            final ClassLoader loader, final ClassPool cp) throws Exception {
        GeneratorParams generatorParams = GeneratorParametersTransformer.getGeneratorParams(loader, classBeingRedefined.getName());

        if (!ClassLoaderHelper.isClassLoderStarted(loader)) {
            return classfileBuffer;
        }

        if (generatorParams == null) {
            return classfileBuffer;
        }

        // flush standard java beans caches
        loader.loadClass("java.beans.Introspector").getMethod("flushCaches").invoke(null);

        if (generatorParams.getParam().getClass().getName().endsWith(".Enhancer")) {
            try {
                return CglibEnhancerProxyTransformer.transform(classBeingRedefined, cp, classfileBuffer, loader, generatorParams);
            } catch (Exception e) {
                LOGGER.error("Error redifining Cglib Enhancer proxy {}", e, classBeingRedefined.getName());
            }
        }

        // Multistep transformation crashed jvm in java8 u05
        if (!isJava8OrNewer)
            try {
                return CglibProxyTransformer.transform(classBeingRedefined, cp, classfileBuffer, generatorParams);
            } catch (Exception e) {
                LOGGER.error("Error redifining Cglib proxy {}", e, classBeingRedefined.getName());
            }

        return classfileBuffer;
    }

    /**
     * Modifies Cglib bytecode generators to store the parameters for this plugin
     *
     * @throws Exception
     */
    @OnClassLoadEvent(classNameRegexp = ".*/cglib/.*", skipSynthetic = false)
    public static CtClass transformDefinitions(CtClass cc) throws Exception {
        try {
            return GeneratorParametersTransformer.transform(cc);
        } catch (Exception e) {
            LOGGER.error("Error modifying class for cglib proxy creation parameter recording", e);
        }
        return cc;
    }
}
