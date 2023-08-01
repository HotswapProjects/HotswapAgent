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
package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacerTransformer;
import org.hotswap.agent.plugin.spring.processor.ConfigurationClassPostProcessorTransformer;
import org.hotswap.agent.plugin.spring.reader.AnnotatedBeanDefinitionReaderTransformer;
import org.hotswap.agent.plugin.spring.reader.ComponentClassTransformer;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerTransformer;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanRefreshCommand;
import org.hotswap.agent.plugin.spring.scanner.PropertiesRefreshCommand;
import org.hotswap.agent.plugin.spring.scanner.SpringBeanTransformer;
import org.hotswap.agent.plugin.spring.scanner.SpringBeanWatchEventListener;
import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerTransformer;
import org.hotswap.agent.plugin.spring.scanner.XmlBeanRefreshCommand;
import org.hotswap.agent.plugin.spring.scanner.XmlFileRefreshCommand;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.Watcher;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Spring plugin.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Spring", description = "Reload Spring configuration after class definition/change.",
        testedVersions = {"All between 3.0.1 - 5.2.2"}, expectedVersions = {"3x", "4x", "5x"},
        supportClass = {ClassPathBeanDefinitionScannerTransformer.class,
                AnnotatedBeanDefinitionReaderTransformer.class,
                ProxyReplacerTransformer.class,
                ConfigurationClassPostProcessorTransformer.class,
                XmlBeanDefinitionScannerTransformer.class})
public class SpringPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringPlugin.class);

    public static String[] basePackagePrefixes;

    @Init
    HotswapTransformer hotswapTransformer;

    @Init
    Watcher watcher;

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    public void init() {
        LOGGER.info("Spring plugin initialized");
        this.registerBasePackageFromConfiguration();
        this.initBasePackagePrefixes();
    }

    public void init(String version) {
        LOGGER.info("Spring plugin initialized - Spring core version '{}'", version);
        this.registerBasePackageFromConfiguration();
        this.initBasePackagePrefixes();
    }

    private void initBasePackagePrefixes() {
        PluginConfiguration pluginConfiguration = new PluginConfiguration(this.appClassLoader);
        if (basePackagePrefixes == null || basePackagePrefixes.length == 0) {
            basePackagePrefixes = pluginConfiguration.getBasePackagePrefixes();
        } else {
            String[] newBasePackagePrefixes = pluginConfiguration.getBasePackagePrefixes();
            List<String> both = new ArrayList<>(basePackagePrefixes.length + newBasePackagePrefixes.length);
            Collections.addAll(both, basePackagePrefixes);
            Collections.addAll(both, newBasePackagePrefixes);
            basePackagePrefixes = both.toArray(new String[both.size()]);
        }
    }

    @OnResourceFileEvent(path = "/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) {
        scheduler.scheduleCommand(new XmlFileRefreshCommand(appClassLoader, url));
    }

    @OnResourceFileEvent(path = "/", filter = ".*.properties", events = {FileEvent.MODIFY})
    public void registerPropertiesListeners(URL url) {
        scheduler.scheduleCommand(new PropertiesRefreshCommand(appClassLoader, url));
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.REDEFINE})
    public void registerClassListeners(Class<?> clazz) {
        scheduler.scheduleCommand(new XmlBeanRefreshCommand(appClassLoader, clazz.getName()));
    }

    /**
     * register base package prefix from configuration file
     */
    public void registerBasePackageFromConfiguration() {
        if (basePackagePrefixes != null) {
            for (String basePackagePrefix : basePackagePrefixes) {
                this.registerBasePackage(basePackagePrefix);
            }
        }
    }

    private void registerBasePackage(final String basePackage) {
        // v.d.: Force load/Initialize ClassPathBeanRefreshCommand classe in JVM. This is hack, in whatever reason sometimes new ClassPathBeanRefreshCommand()
        //       stays locked inside agent's transform() call. It looks like some bug in JVMTI or JVMTI-debugger() locks handling.
        ClassPathBeanRefreshCommand fooCmd = new ClassPathBeanRefreshCommand();
        hotswapTransformer.registerTransformer(appClassLoader, getClassNameRegExp(basePackage),
                new SpringBeanTransformer(appClassLoader, scheduler, basePackage));
    }

    /**
     * Register both hotswap transformer AND watcher - in case of new file the file is not known
     * to JVM and hence no hotswap is called. The file may even exist, but until is loaded by Spring
     * it will not be known by the JVM. File events are processed only if the class is not known to the
     * classloader yet.
     *
     * @param basePackage only files in a basePackage
     */
    public void registerComponentScanBasePackage(final String basePackage) {
        LOGGER.info("Registering basePackage {}", basePackage);

        this.registerBasePackage(basePackage);

        Enumeration<URL> resourceUrls = null;
        try {
            resourceUrls = getResources(basePackage);
        } catch (IOException e) {
            LOGGER.error("Unable to resolve base package {} in classloader {}.", basePackage, appClassLoader);
            return;
        }

        // for all application resources watch for changes
        while (resourceUrls.hasMoreElements()) {
            URL basePackageURL = resourceUrls.nextElement();

            if (!IOUtils.isFileURL(basePackageURL)) {
                LOGGER.debug("Spring basePackage '{}' - unable to watch files on URL '{}' for changes (JAR file?), limited hotswap reload support. " +
                        "Use extraClassPath configuration to locate class file on filesystem.", basePackage, basePackageURL);
            } else {
                watcher.addEventListener(appClassLoader, basePackageURL,
                        new SpringBeanWatchEventListener(scheduler, appClassLoader, basePackage));
            }
        }
    }

    /**
     * Register a hotswap transformer for individual component class registration.
     *
     * @param clazz component class
     * @see AnnotatedBeanDefinitionReader#register(Class[])
     */
    public void registerComponentClass(String clazz) {
        LOGGER.info("Registering component class {}", clazz);
        hotswapTransformer.registerTransformer(appClassLoader, getClassNameRegExp(clazz),
                new ComponentClassTransformer(appClassLoader, scheduler, clazz));
    }

    private String getClassNameRegExp(String basePackage) {
        String regexp = basePackage;
        while (regexp.contains("**")) {
            regexp = regexp.replace("**", ".*");
        }
        if (!regexp.endsWith(".*")) {
            regexp += ".*";
        }
        return regexp;
    }

    private Enumeration<URL> getResources(String basePackage) throws IOException {
        String resourceName = basePackage;
        int index = resourceName.indexOf('*');
        if (index != -1) {
            resourceName = resourceName.substring(0, index);
            index = resourceName.lastIndexOf('.');
            if (index != -1) {
                resourceName = resourceName.substring(0, index);
            }
        }
        resourceName = resourceName.replace('.', '/');
        return appClassLoader.getResources(resourceName);
    }

    /**
     * Plugin initialization is after Spring has finished its startup and freezeConfiguration is called.
     *
     * This will override freeze method to init plugin - plugin will be initialized and the configuration
     * remains unfrozen, so bean (re)definition may be done by the plugin.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory")
    public static void register(CtClass clazz) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append("setCacheBeanMetadata(false);");
        // init a spring plugin with every appclassloader
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
                "org.hotswap.agent.plugin.spring.ResetSpringStaticCaches.resetBeanNamesByType(this); " +
                "setAllowRawInjectionDespiteWrapping(true); ");

        // Patch registerBeanDefinition so that XmlBeanDefinitionScannerAgent has chance to keep track of all beans
        // defined from the XML configuration.
        CtMethod registerBeanDefinitionMethod = clazz.getDeclaredMethod("registerBeanDefinition");
        registerBeanDefinitionMethod.insertBefore(
                "org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent.registerBean($1, $2);"
        );
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.aop.framework.CglibAopProxy")
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
