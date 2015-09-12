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
        description = "Weld framework(http://weld.cdi-spec.org/). Support hotswapping for Jboss Weld/CDI.",
        testedVersions = {"2.2.6"},
        expectedVersions = {"All between 2.0 - 2.2"})
public class WeldPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldPlugin.class);
    static boolean IS_TEST_ENVIRONMENT = Boolean.FALSE;

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

    public void init() {
        LOGGER.info("CDI/Weld plugin initialized.");
    }

    /**
     * Register watcher - in case of new file the file is not known
     * to JVM and hence no hotswap is called.
     *
     * @param bdaId the Bean Deployment Archive ID
     */
    public synchronized void registerBeanDeplArchivePath(final String bdaId, final String archivePath) {
        LOGGER.info("Registering path {}", archivePath);

        URL resource = null;
        try {
            resource = resourceNameToURL(archivePath);
            URI uri = resource.toURI();
            if (!IOUtils.isFileURL(uri.toURL())) {
                LOGGER.debug("Weld - unable to watch files on URL '{}' for changes (JAR file?)", archivePath);
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
                            if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className) || IS_TEST_ENVIRONMENT) {
                                // refresh weld only for new classes
                                LOGGER.trace("register reload command: {} ", className);
                                scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader, bdaId, archivePath, event), WAIT_ON_CREATE);
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
                bdaId = new File(bdaId).toPath().toString();
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
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "init"));
        src.append("}");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertBeforeBody(src.toString());
        }

        LOGGER.debug("org.jboss.weld.bootstrap.WeldBootstrap enhanced with plugin initialization.");
    }

    /**
     * Basic WeldBeanDeploymentArchive transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
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
            "org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent.registerArchive(this);"
        );

        LOGGER.debug("Class 'org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive' patched with basePackage registration.");
    }

    /**
     * Jboss BeanDeploymentArchiveImpl transformation
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl")
    public static void transformJbossBda(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "init"));
        src.append("if (beanArchiveType!=null && \"EXPLICIT\".equals(beanArchiveType.toString())){");
        src.append("  String beanXml = \"META-INF/beans.xml\";");
        src.append("  java.util.List resList = module.getClassLoader().loadResourceLocal(beanXml);");
        src.append("  if(resList.size() == 1) {");
        src.append("    org.jboss.modules.Resource res = (org.jboss.modules.Resource) resList.get(0);");
        src.append("    String archPath = res.getURL().getPath();");
        src.append("    archPath = archPath.substring(0, archPath.length()-beanXml.length());");
        src.append("    org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent.registerArchive(this,archPath);");
        src.append("  }");
        src.append("}}");
        String a;

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class 'org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl' patched with basePackage registration.");
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
}
