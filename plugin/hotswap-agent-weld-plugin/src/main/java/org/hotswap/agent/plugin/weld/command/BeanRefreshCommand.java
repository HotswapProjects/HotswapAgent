package org.hotswap.agent.plugin.weld.command;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * BeanRefreshCommand. When a bean class is redefined object of ClassPathBeanRefreshCommand is created and after
 * timeout executed. It calls bean reload logic in BeanDepoymentArchiveAagent internally
 *
 * @author Vladimir Dvorak
 */
public class BeanRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanRefreshCommand.class);

    ClassLoader classLoader;

    String archivePath;

    String className;

    // either event or classDefinition is set by constructor (watcher or transformer)
    WatchFileEvent event;

    public BeanRefreshCommand(ClassLoader classLoader, String archivePath, String className) {
        this.classLoader = classLoader;
        this.archivePath = archivePath;
        this.className = className;
    }

    public BeanRefreshCommand(ClassLoader classLoader, String archivePath, WatchFileEvent event) {
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
        }
    }

    @Override
    public void executeCommand() {

        if (isDeleteEvent()) {
            LOGGER.trace("Skip Weld reload for delete event on class '{}'", className);
            return;
        }

        try {
            LOGGER.debug("Executing BeanDeploymentArchiveAgent.refreshClass('{}')", className);
            Class<?> bdaAgentClazz = Class.forName(BeanDeploymentArchiveAgent.class.getName(), true, classLoader);
            Method bdaMethod  = bdaAgentClazz.getDeclaredMethod("refreshClass", new Class[] {ClassLoader.class, String.class, String.class});
            bdaMethod.invoke(null, classLoader, archivePath, className);
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

    /**
     * Check all merged events for delete and create events. If delete without create is found, than assume
     * file was deleted.
     */
    private boolean isDeleteEvent() {
        // for all merged commands including this command
        List<BeanRefreshCommand> mergedCommands = new ArrayList<BeanRefreshCommand>();
        for (Command command : getMergedCommands()) {
            mergedCommands.add((BeanRefreshCommand) command);
        }
        mergedCommands.add(this);

        boolean createFound = false;
        boolean deleteFound = false;
        for (BeanRefreshCommand command : mergedCommands) {
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

        BeanRefreshCommand that = (BeanRefreshCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;
        if (!className.equals(that.className)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        result = 31 * result + className.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ClassPathBeanRefreshCommand{" +
                "classLoader=" + classLoader +
                ", archivePath='" + archivePath + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
