package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerTransformer;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanRefreshCommand;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Enumeration;

/**
 * Spring plugin.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Spring", description = "Reload Spring configuration after class definition/change.",
        testedVersions = {"3.0.0", "3.1.4", "3.2.1", "4.0.2"}, expectedVersions = {"3x", "4x"},
        supportClass = {ClassPathBeanDefinitionScannerTransformer.class})
public class SpringPlugin {
    /**
     * If a class is modified in IDE, sequence of multiple events is generated -
     * class file DELETE, CREATE, MODIFY, than Hotswap transformer is invoked.
     * ClassPathBeanRefreshCommand tries to merge these events into single command.
     * Wait this this timeout for hotswap command after class file event, it will
     * never occur if this really is a new class.
     */
    private static final int WAIT_FOR_HOTSWAP_ON_CREATE = 300;

    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringPlugin.class);

    @Init
    HotswapTransformer hotswapTransformer;

    @Init
    Watcher watcher;

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    public void init() {
        LOGGER.info("Spring plugin initialized - Spring core version");
    }
    public void init(String version) {
        LOGGER.info("Spring plugin initialized - Spring core version '{}'", version);
    }

    /**
     * Register both hotswap transformer AND watcher - in case of new file the file is not known
     * to JVM and hence no hotswap is called. The file may even exist, but until is loaded by Spring
     * it will not be known by the JVM. Hotswap command has priority (watcher will try to wait for hotswap
     * up to 500 ms).
     * @param basePackage only files in a basePackage
     */
    public void registerComponentScanBasePackage(final String basePackage) {
        LOGGER.info("Registering basePackage {}", basePackage);

        hotswapTransformer.registerTransformer(appClassLoader, basePackage + ".*", new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (classBeingRedefined != null) {
                    scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                        basePackage, className, classfileBuffer));
                }
                return classfileBuffer;
            }
        });

        Enumeration<URL> resourceUrls = null;
        try {
            resourceUrls = appClassLoader.getResources(basePackage.replace(".", "/"));
        } catch (IOException e) {
            LOGGER.error("Unable to resolve base package {} in classloader {}.", basePackage, appClassLoader);
            return;
        }

        while (resourceUrls.hasMoreElements()) {
            URL basePackageURL = resourceUrls.nextElement();

            if (!IOUtils.isFileURL(basePackageURL)) {
                LOGGER.debug("Spring basePackage '{}' - unable to watch files on URL '{}' for changes (JAR file?), limited hotswap reload support. " +
                        "Use extraClassPath configuration to locate class file on filesystem.", basePackage, basePackageURL);
                continue;
            } else {
                watcher.addEventListener(appClassLoader, basePackageURL, new WatchEventListener() {
                    @Override
                    public void onEvent(WatchEvent event) {
                        if (event.isFile() && event.getURI().toString().endsWith(".class")) {
                            scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                                    basePackage, event), WAIT_FOR_HOTSWAP_ON_CREATE);
                        }
                    }
                });
            }
        }
    }

    /**
     * Plugin initialization is after Spring has finished its startup and freezeConfiguration is called.
     *
     * This will override freeze method to init plugin - plugin will be initialized and the configuration
     * remains unfrozen, so bean (re)definition may be done by the plugin.
     */
    @Transform(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory")
    public static void register(CtClass clazz) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append("setCacheBeanMetadata(false);");
        src.append(PluginManagerInvoker.buildInitializePlugin(SpringPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(SpringPlugin.class, "init",
                "org.springframework.core.SpringVersion.getVersion()", String.class.getName()));
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertBeforeBody(src.toString());
        }

        // freezeConfiguration cannot be disabled because of performance degradation
        // instead call freezeConfiguration after each bean (re)definition and clear all caches.

        // WARNING - allowRawInjectionDespiteWrapping is not safe, however without this
        //   spring was not able to resolve circular references correctly.
        //   However, the code in AbstractAutowireCapableBeanFactory.doCreateBean() in debugger always
        //   showed that exposedObject == earlySingletonReference and hence everything is Ok.
        // 				if (exposedObject == bean) {
        //                  exposedObject = earlySingletonReference;
        //   The waring is because I am not sure what is going on here.

        CtMethod method = clazz.getDeclaredMethod("freezeConfiguration");
        method.insertBefore(
                "org.hotswap.agent.plugin.spring.ResetSpringStaticCaches.resetBeanNamesByType(); " +
                "setAllowRawInjectionDespiteWrapping(true); ");
    }

    @Transform(classNameRegexp = "org.springframework.aop.framework.CglibAopProxy")
    public static void cglibAopProxyDisableCache(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("createEnhancer");
        method.setBody("{" +
                "org.springframework.cglib.proxy.Enhancer enhancer = new org.springframework.cglib.proxy.Enhancer();" +
                "enhancer.setUseCache(false);" +
                "return enhancer;" +
                "}");

        LOGGER.debug("org.springframework.aop.framework.CglibAopProxy - cglib Enhancer cache disabled");
    }
}
