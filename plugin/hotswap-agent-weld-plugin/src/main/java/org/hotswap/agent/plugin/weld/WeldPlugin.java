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
import org.hotswap.agent.plugin.weld.command.BdaAgentRegistry;
import org.hotswap.agent.plugin.weld.command.ClassPathBeanRefreshCommand;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
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

    boolean inJbossAS = false;

    boolean initialized = false;

    public void init() {
        if (!initialized) {
            LOGGER.info("CDI/Weld plugin initialized.");
            initialized = true;
        }
    }

    public void initInJBossAS() {
        if (!initialized) {
            LOGGER.info("CDI/Weld plugin initialized in JBossAS.");
            inJbossAS = true;
            initialized = true;
        }
    }

    /**
     * Register BeanDeploymentArchive by bdaId to watcher. In case of new class the class file is not known
     * to JVM hence no hotswap is called and therefore it must be handled by watcher.
     *
     * @param bdaId the BeanDeploymentArchive ID
     */
    public synchronized void registerBeanDeplArchivePath(final String archivePath) {
        LOGGER.info("Registering archive path {}", archivePath);

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
                                if (isBdaRegistered(appClassLoader, archivePath)) {
                                    scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader, archivePath, event), WAIT_ON_CREATE);
                                }
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
    public void classReload(ClassLoader classLoader, CtClass ctClass, Class original) {
        if (original != null) {
            try {
                String classFilePath = ctClass.getURL().getPath();
                String className = ctClass.getName().replace(".", "/");
                // archive path ends with '/' therefore we set end position before the '/' (-1)
                String archivePath = classFilePath.substring(0, classFilePath.indexOf(className) - 1);
                archivePath = new File(archivePath).toPath().toString();
                if (isBdaRegistered(appClassLoader, archivePath)) {
                    scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(classLoader, archivePath, original.getName()), WAIT_ON_CREATE);
                }
            } catch (Exception e) {
                LOGGER.error("classReload() exception {}.", e.getMessage());
            }
        }
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

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "init"));
        src.append("org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent.registerArchive(getClass().getClassLoader(), this, this.getId());");
        src.append("}");

        CtConstructor declaredConstructor = clazz.getDeclaredConstructor(constructorParams);
        declaredConstructor.insertAfter(src.toString());

        LOGGER.debug("Class 'org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive' patched with BDA registration.");
    }

    /**
     * Jboss BeanDeploymentArchiveImpl transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl")
    public static void transformJbossBda(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append("if (beansXml!=null&& beanArchiveType!=null && \"EXPLICIT\".equals(beanArchiveType.toString())){");
        src.append("  String beansXmlPath = beansXml.getUrl().getPath();");
        src.append("  String archPath = null;");
        src.append("  if(beansXmlPath.endsWith(\"META-INF/beans.xml\")) {");
        src.append("    archPath = beansXmlPath.substring(0, beansXmlPath.length()-\"META-INF/beans.xml\".length()-1);"); /* -1 ~ eat "/" at the end of path */
        src.append("  } else if (beansXmlPath.endsWith(\"WEB-INF/beans.xml\")) {");
        src.append("    archPath = beansXmlPath.substring(0, beansXmlPath.length()-\"beans.xml\".length()) + \"classes\";");
        src.append("  }");
        src.append("  if(archPath != null) {");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class, "module.getClassLoader()"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("module.getClassLoader()", WeldPlugin.class, "initInJBossAS"));
        src.append("    Class agC = Class.forName(\"org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent\", true, module.getClassLoader());");
        src.append("    java.lang.reflect.Method agM  = agC.getDeclaredMethod(\"registerArchive\", new Class[] {java.lang.ClassLoader.class, org.jboss.weld.bootstrap.spi.BeanDeploymentArchive.class, java.lang.String.class});");
        src.append("    agM.invoke(null, new Object[] { module.getClassLoader(),this,archPath });");
        src.append("  }");
        src.append("}}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class 'org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl' patched with BDA registration.");
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

    private static boolean isBdaRegistered(ClassLoader classLoader, String archivePath) {
        try {
            return (boolean) ReflectionHelper.invoke(null, Class.forName(BdaAgentRegistry.class.getName(), true, classLoader),
                    "contains", new Class[] {String.class}, archivePath);
        } catch (ClassNotFoundException e) {
            LOGGER.error("isBdaRegistered() exception {}.", e.getMessage());
        }
        return false;
    }

}
