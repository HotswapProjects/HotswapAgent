package org.hotswap.agent.plugin.weld;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.command.ClassPathBeanRefreshCommand;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * WeldPlugin
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "Weld",
        description = "Support hotswapping in Jboss Weld CDI framework.",
        testedVersions = {"2.2.6"},
        expectedVersions = {"All between 2.0 - 2.2"})
public class WeldPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldPlugin.class);

    /**
     * If a class is modified in IDE, sequence of multiple events is generated -
     * class file DELETE, CREATE, MODIFY, than Hotswap transformer is invoked.
     * ClassPathBeanRefreshCommand tries to merge these events into single command.
     * Wait this this timeout after class file event.
     */
    private static final int WAIT_ON_CREATE = 600;

    @Init
    Watcher watcher;

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

//    private Map<Object, Object> registeredBeans = new WeakHashMap<Object, Object>();
//    private Map<String, CtClass> hotswappedCtClassMap = new HashMap<String, CtClass>();

    public void registerBeanDeplArchivePath(final String bdaPath) {
        LOGGER.info("Registering path {}", bdaPath);

        URL resource = null;
        try {
            resource = resourceNameToURL(bdaPath);
            URI uri = resource.toURI();
            if (!IOUtils.isFileURL(uri.toURL())) {
                LOGGER.debug("Weld - unable to watch files on URL '{}' for changes (JAR file?)", bdaPath);
                return;
            } else {
                watcher.addEventListener(appClassLoader, uri, new WatchEventListener() {
                    @Override
                    public void onEvent(WatchFileEvent event) {
                        if (event.isFile() && event.getURI().toString().endsWith(".class")) {
                            // check that the class is not loaded by the classloader yet (avoid duplicate reload)
                            String className;
                            try {
                                className = IOUtils.urlToClassName(event.getURI());
                            } catch (IOException e) {
                                LOGGER.trace("Watch event on resource '{}' skipped, probably Ok because of delete/create event sequence (compilation not finished yet).", e, event.getURI());
                                return;
                            }
                            if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className)) {
                                // refresh weld only for new classes
                                scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                                        bdaPath, event), WAIT_ON_CREATE);
                            }
                        }
                    }
                });
            }
        } catch (URISyntaxException e) {
            LOGGER.error("Unable to watch path '{}' for changes.", e, resource);
        } catch (Exception e) {
            LOGGER.warning("registerBeanDeplArchivePath() exception : {}",  e.getMessage());
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(CtClass ctClass, Class original) {
        if (original != null) {
            try {
                String classFilePath = ctClass.getURL().getPath();
                String className = ctClass.getName().replace(".", "/");
                // archive path ends with '/' therefore we set end position before the '/' (-1)
                String bdaId = classFilePath.substring(0, classFilePath.indexOf(className) - 1);
                scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader, bdaId, original.getName()), WAIT_ON_CREATE);
            } catch (Exception e) {
                LOGGER.warning("classReload() exception : {}",  e.getMessage());
            }
        }
    }

    /**
     * Add plugin initialization at the end of weld initialization.
     *
     * @param ctClass the WeldBootstrap ctClass
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bootstrap.WeldBootstrap")
    public static void weldBootstrapInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("startInitialization");
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
        LOGGER.debug("org.jboss.weld.bootstrap.WeldBootstrap enhanced with plugin initialization.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("java.lang.String"),
            classPool.get("java.util.Collection"),
            classPool.get("org.jboss.weld.bootstrap.spi.BeansXml"),
            classPool.get("java.util.Set")
        };

        CtConstructor declaredConstructor = clazz.getDeclaredConstructor(constructorParams);
        declaredConstructor.insertAfter(
            "org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent.registerArchive(this).register();"
        );

        LOGGER.debug("Class 'org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive' patched with basePackage registration.");
    }

    private URL resourceNameToURL(String resource) throws Exception {
        try {
            // Try to format as a URL?
            return new URL(resource);
        } catch (MalformedURLException e) {
            // try to locate a file
            if (resource.startsWith("./"))
                resource = resource.substring(2);
            File file = new File(resource).getCanonicalFile();
            return file.toURI().toURL();
        }
    }

    /**
     * Patch proxy factory class.
     *   - add factory registration into constructors
     *   - add recreateProxyClass method
     *
     * @param ctClass the ProxyFactory class
     */

 /*
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bean.proxy.ProxyFactory")
    public static void patchProxyFactory(CtClass ctClass) throws NotFoundException, CannotCompileException {
        String registerProxyFactory = PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "registerProxyFactory",
        		"this", "java.lang.Object", "bean", "java.lang.Object");
        String initializeThis = PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "initWeldPlugin");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerProxyFactory);
            constructor.insertAfter(initializeThis);
        }

        CtMethod recreateMethod = CtNewMethod.make(buildRecreateMethod(), ctClass);
        ctClass.addMethod(recreateMethod);
    }

    private static String buildRecreateMethod() {
    	StringBuilder method = new StringBuilder();
        method.append("public void recreateProxyClass() {");
        method.append("	        String suffix = \"_$$_Weld\" + getProxyNameSuffix();");
        method.append("	        String proxyClassName = getBaseProxyName();");
        method.append("	        if (!proxyClassName.endsWith(suffix)) {");
        method.append("	            proxyClassName = proxyClassName + suffix;");
        method.append("	        }");
        method.append("	        if (proxyClassName.startsWith(JAVA)) {");
        method.append("	            proxyClassName = proxyClassName.replaceFirst(JAVA, \"org.jboss.weld\");");
        method.append("	        }");
        method.append("	        createProxyClass(proxyClassName);");
        method.append("}");
    	return method.toString();
	}
*/

    /**
     * Plugin initialization is called as a last step of weld initialization
     *
     */

/*
    public void initWeldPlugin() {
        if (recreateProxyFactoryCommand != null)
            return;

        recreateProxyFactoryCommand = new RecreateProxyClassCommand(
                appClassLoader, hotswappedCtClassMap, registeredBeans);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void reloadBeanClass(CtClass ctClass, Class original)
            throws Exception {
        synchronized (hotswappedCtClassMap) {
            hotswappedCtClassMap.put(ctClass.getName(), ctClass);
        }
        scheduler.scheduleCommand(recreateProxyFactoryCommand);
    }

    public void registerProxyFactory(Object proxyFactory, Object bean) {
        synchronized (registeredBeans) {
            registeredBeans.put(bean, proxyFactory);
        }
        LOGGER.debug("WeldPlugin - registerProxyFactory : "
                + proxyFactory.getClass().getName());
    }
*/
}
