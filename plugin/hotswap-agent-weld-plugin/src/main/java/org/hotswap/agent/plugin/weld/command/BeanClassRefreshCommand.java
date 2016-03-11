package org.hotswap.agent.plugin.weld.command;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * BeanClassRefreshCommand. When a bean class is redefined object of BeanClassRefreshCommand is created and after
 * timeout executed. It calls bean reload logic in BeanDepoymentArchiveAgent internally
 *
 * @author Vladimir Dvorak
 */
public class BeanClassRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanClassRefreshCommand.class);

    ClassLoader classLoader;

    String archivePath;

    String className;

    String classSignature;

    Map<Object, Object> registeredProxiedBeans;

    // either event or classDefinition is set by constructor (watcher or transformer)
    WatchFileEvent event;

    public BeanClassRefreshCommand(ClassLoader classLoader, String archivePath,
            Map<Object, Object> registeredProxiedBeans, String className, String classSignature) {
        this.classLoader = classLoader;
        this.archivePath = archivePath;
        this.registeredProxiedBeans = registeredProxiedBeans;
        this.className = className;
        this.classSignature = classSignature;
    }

    public BeanClassRefreshCommand(ClassLoader classLoader, String archivePath, WatchFileEvent event) {
        this.classLoader = classLoader;
        this.archivePath = archivePath;
        this.event = event;

        // strip from URI prefix up to basePackage and .class suffix.
        String classFullPath = Paths.get(event.getURI()).toString();
        int index = classFullPath.indexOf(archivePath);
        if (index == 0) {
            String classPath = classFullPath.substring(archivePath.length());
            classPath = classPath.substring(0, classPath.indexOf(".class"));
            if (classPath.startsWith(File.separator)) {
                classPath = classPath.substring(1);
            }
            this.className = classPath.replace(File.separator, ".");
        } else {
            LOGGER.error("Archive path '{}' doesn't match with classFullPath '{}'", archivePath, classFullPath);
        }
    }

    @Override
    public void executeCommand() {
        if (isDeleteEvent()) {
            LOGGER.trace("Skip Weld reload for delete event on class '{}'", className);
            return;
        }
        if (className != null) {
            try {
                LOGGER.debug("Executing BeanDeploymentArchiveAgent.refreshBeanClass('{}')", className);
                Class<?> bdaAgentClazz = Class.forName(BeanDeploymentArchiveAgent.class.getName(), true, classLoader);
                Method bdaMethod  = bdaAgentClazz.getDeclaredMethod("refreshBeanClass",
                        new Class[] {ClassLoader.class, String.class, Map.class, String.class, String.class});
                bdaMethod.invoke(null, classLoader, archivePath, registeredProxiedBeans, className, classSignature);
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
     */
    private boolean isDeleteEvent() {
        // for all merged commands including this command
        List<BeanClassRefreshCommand> mergedCommands = new ArrayList<BeanClassRefreshCommand>();
        for (Command command : getMergedCommands()) {
            mergedCommands.add((BeanClassRefreshCommand) command);
        }
        mergedCommands.add(this);

        boolean createFound = false;
        boolean deleteFound = false;
        for (BeanClassRefreshCommand command : mergedCommands) {
            if (command.event != null) {
                if (command.event.getEventType().equals(FileEvent.DELETE))
                    deleteFound = true;
                if (command.event.getEventType().equals(FileEvent.CREATE))
                    createFound = true;
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
        if (className != null && !className.equals(that.className)) return false;

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
