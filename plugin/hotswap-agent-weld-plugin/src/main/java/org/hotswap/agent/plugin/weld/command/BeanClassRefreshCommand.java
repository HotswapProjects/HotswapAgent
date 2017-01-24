package org.hotswap.agent.plugin.weld.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.BeanReloadStrategy;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * BeanClassRefreshCommand. If a bean class is redefined, an object of BeanClassRefreshCommand is created and after
 *  a timeout executed. It calls bean reload logic in BeanDepoymentArchiveAgent internally
 *
 * @author Vladimir Dvorak
 */
public class BeanClassRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanClassRefreshCommand.class);

    ClassLoader classLoader;

    String archivePath;

    String className;

    String classSignatureForProxyCheck;

    String classSignatureByStrategy;

    String strBeanReloadStrategy;

    Map<Object, Object> registeredProxiedBeans;

    // either event or classDefinition is set by constructor (watcher or transformer)
    WatchFileEvent event;

    public BeanClassRefreshCommand(ClassLoader classLoader, String archivePath, Map<Object, Object> registeredProxiedBeans,
            String className, String classSignaturForProxyCheck, String classSignatureByStrategy, BeanReloadStrategy beanReloadStrategy) {
        this.classLoader = classLoader;
        this.archivePath = archivePath;
        this.registeredProxiedBeans = registeredProxiedBeans;
        this.className = className;
        this.classSignatureForProxyCheck = classSignaturForProxyCheck;
        this.classSignatureByStrategy = classSignatureByStrategy;
        this.strBeanReloadStrategy = beanReloadStrategy != null ? beanReloadStrategy.toString() : null;
    }

    public BeanClassRefreshCommand(ClassLoader classLoader, String normalizedArchivePath, WatchFileEvent event) {
        this.classLoader = classLoader;
        this.archivePath = normalizedArchivePath;
        this.event = event;

        // strip from URI prefix up to basePackage and .class suffix.
        String classFullPath = event.getURI().getPath();
        int index = classFullPath.indexOf(normalizedArchivePath);
        if (index == 0) {
            String classPath = classFullPath.substring(normalizedArchivePath.length());
            classPath = classPath.substring(0, classPath.indexOf(".class"));
            if (classPath.startsWith("/")) {
                classPath = classPath.substring(1);
            }
            this.className = classPath.replace("/", ".");
        } else {
            LOGGER.error("Archive path '{}' doesn't match with classFullPath '{}'", normalizedArchivePath, classFullPath);
        }
    }

    public void executeCommand() {
        try {
            List<BeanClassRefreshCommand> mergedCommands = new ArrayList<BeanClassRefreshCommand>();
            mergedCommands.add(this);
            for (Command command : getMergedCommands()) {
                mergedCommands.add((BeanClassRefreshCommand) command);
            }

            // First step : recreate all proxies
            for (BeanClassRefreshCommand command: mergedCommands) {
                command.recreateProxy(mergedCommands);
            }

            // Second step : reload beans
            for (BeanClassRefreshCommand command: mergedCommands) {
                command.reloadBean(mergedCommands);
            }
        } finally {
        }
    }

    private void recreateProxy(List<BeanClassRefreshCommand> mergedCommands) {

        if (isDeleteEvent(mergedCommands)) {
            LOGGER.trace("Skip recreate proxy for delete event on class '{}'", className);
            return;
        }

        if (className != null) {
            try {
                LOGGER.debug("Executing BeanDeploymentArchiveAgent.recreateProxy('{}')", className);
                Class<?> bdaAgentClazz = Class.forName(BeanDeploymentArchiveAgent.class.getName(), true, classLoader);
                Method recreateProxy  = bdaAgentClazz.getDeclaredMethod("recreateProxy",
                        new Class[] { ClassLoader.class,
                                      String.class,
                                      Map.class,
                                      String.class,
                                      String.class
                        }
                );
                recreateProxy.invoke(null,
                        classLoader,
                        archivePath,
                        registeredProxiedBeans,
                        className,
                        classSignatureForProxyCheck
                );
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Plugin error, method not found", e);
            } catch (InvocationTargetException e) {
                LOGGER.error("Error recreate proxy class {} in classLoader {}", e, className, classLoader);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Plugin error, illegal access", e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
            }
        }
    }

    private void reloadBean(List<BeanClassRefreshCommand> mergedCommands) {

        if (isDeleteEvent(mergedCommands)) {
            LOGGER.trace("Skip refresh bean class for delete event on class '{}'", className);
            return;
        }

        if (className != null) {
            try {
                LOGGER.debug("Executing BeanDeploymentArchiveAgent.refreshBeanClass('{}')", className);
                Class<?> bdaAgentClazz = Class.forName(BeanDeploymentArchiveAgent.class.getName(), true, classLoader);
                Method refreshBean  = bdaAgentClazz.getDeclaredMethod("reloadBean",
                        new Class[] { ClassLoader.class,
                                      String.class,
                                      String.class,
                                      String.class,
                                      String.class
                        }
                );
                refreshBean.invoke(null,
                        classLoader,
                        archivePath,
                        className,
                        classSignatureByStrategy,
                        strBeanReloadStrategy // passed as String since BeanDeploymentArchiveAgent has different classloader
                );
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Plugin error, method not found", e);
            } catch (InvocationTargetException e) {
                LOGGER.error("Error refreshing class {} in classLoader {}", e, className, classLoader);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Plugin error, illegal access", e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
            }
        }
    }

    /**
     * Check all merged events for delete and create events. If delete without create is found, than assume
     * file was deleted.
     * @param mergedCommands
     */
    private boolean isDeleteEvent(List<BeanClassRefreshCommand> mergedCommands) {
        boolean createFound = false;
        boolean deleteFound = false;
        for (BeanClassRefreshCommand command : mergedCommands) {
            if (className.equals(command.className)) {
                if (command.event != null) {
                    if (command.event.getEventType().equals(FileEvent.DELETE))
                        deleteFound = true;
                    if (command.event.getEventType().equals(FileEvent.CREATE))
                        createFound = true;
                }
            }
        }

        LOGGER.trace("isDeleteEvent result {}: createFound={}, deleteFound={}", createFound, deleteFound);
        return !createFound && deleteFound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BeanClassRefreshCommand that = (BeanClassRefreshCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;
        if (archivePath != null && !archivePath.equals(that.archivePath)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        result = 31 * result + (className != null ? className.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BeanClassRefreshCommand{" +
                "classLoader=" + classLoader +
                ", archivePath='" + archivePath + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
