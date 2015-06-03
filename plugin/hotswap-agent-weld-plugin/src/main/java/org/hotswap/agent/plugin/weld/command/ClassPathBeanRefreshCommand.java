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

public class ClassPathBeanRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanRefreshCommand.class);

    ClassLoader appClassLoader;

    String archivePath;

    String className;

    // either event or classDefinition is set by constructor (watcher or transformer)
    WatchFileEvent event;

    public ClassPathBeanRefreshCommand(ClassLoader appClassLoader, String archivePath, String className) {
        this.appClassLoader = appClassLoader;
        this.archivePath = archivePath;
        this.className = className;
    }

    public ClassPathBeanRefreshCommand(ClassLoader appClassLoader, String archivePath, WatchFileEvent event) {
        this.appClassLoader = appClassLoader;
        this.archivePath = archivePath;
        this.event = event;

        // strip from URI prefix up to basePackage and .class suffix.
        String classFullPath = Paths.get(event.getURI()).toString();
        int indx = classFullPath.indexOf(archivePath);
        if (indx == 0)
        {
            String classPath = classFullPath.substring(archivePath.length());
            classPath = classPath.substring(0, classPath.indexOf(".class"));
            if (classPath.startsWith(File.separator))
            {
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
            LOGGER.debug("Executing ClassPathBeanDefinitionScannerAgent.refreshBean('{}')", className);

            Class<?> clazz = appClassLoader.loadClass(BeanDeploymentArchiveAgent.class.getName());
            Method method  = clazz.getDeclaredMethod("refreshClass", new Class[] {String.class, String.class});
            method.invoke(null, archivePath, className);

        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in classLoader {}", e, className, appClassLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        }

    }

    /**
     * Check all merged events for delete and create events. If delete without create is found, than assume
     * file was deleted.
     */
    private boolean isDeleteEvent() {
        // for all merged commands including this command
        List<ClassPathBeanRefreshCommand> mergedCommands = new ArrayList<ClassPathBeanRefreshCommand>();
        for (Command command : getMergedCommands()) {
            mergedCommands.add((ClassPathBeanRefreshCommand) command);
        }
        mergedCommands.add(this);

        boolean createFound = false;
        boolean deleteFound = false;
        for (ClassPathBeanRefreshCommand command : mergedCommands) {
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

        ClassPathBeanRefreshCommand that = (ClassPathBeanRefreshCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;
        if (!className.equals(that.className)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        result = 31 * result + className.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ClassPathBeanRefreshCommand{" +
                "appClassLoader=" + appClassLoader +
                ", basePackage='" + archivePath + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
