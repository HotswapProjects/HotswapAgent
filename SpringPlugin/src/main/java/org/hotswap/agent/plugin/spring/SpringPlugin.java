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
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchEvent;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Spring plugin.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Spring", testedVersions = {"3.1"},
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

    public void registerComponentScanBasePackage(final String basePackage) {
        LOGGER.info("Registering basePackage {}", basePackage);

        hotswapTransformer.registerTransformer(basePackage + ".*", new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (classBeingRedefined != null) {
                    scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                        basePackage, className, classfileBuffer));
                }
                return classfileBuffer;
            }
        });

        watcher.addEventListener(appClassLoader.getResource(basePackage.replace(".", "/")), new WatchEventListener() {
            @Override
            public void onEvent(WatchEvent event) {
                if (event.getEventType().equals(WatchEvent.WatchEventType.CREATE)) {
                    scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                            basePackage, event.getURI()), WAIT_FOR_HOTSWAP_ON_CREATE);
                }
            }
        });
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

        CtMethod method = clazz.getDeclaredMethod("freezeConfiguration");
        method.setBody("{}");
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
