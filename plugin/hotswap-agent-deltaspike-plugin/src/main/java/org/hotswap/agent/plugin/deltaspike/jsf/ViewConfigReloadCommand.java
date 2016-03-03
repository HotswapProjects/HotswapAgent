package org.hotswap.agent.plugin.deltaspike.jsf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

public class ViewConfigReloadCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ViewConfigReloadCommand.class);

    ClassLoader classLoader;
    Object viewConfigExtension;
    String viewConfigRootClassName;

    public ViewConfigReloadCommand(ClassLoader classLoader, Object viewConfigExtension, String resolverRootClassName) {
        this.classLoader = classLoader;
        this.viewConfigExtension = viewConfigExtension;
        this.viewConfigRootClassName = resolverRootClassName;
    }

    @Override
    public void executeCommand() {
        try {
            LOGGER.debug("Executing ViewConfigReloader.reloadViewConfig('{}')", viewConfigRootClassName);
            Class<?> reloaderClazz = classLoader.loadClass(ViewConfigReloader.class.getName());
            Method m  = reloaderClazz.getDeclaredMethod("reloadViewConfig", new Class[] {ClassLoader.class, Object.class, String.class});
            m.invoke(null, classLoader, viewConfigExtension, viewConfigRootClassName);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in classLoader {}", e, viewConfigRootClassName, classLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ViewConfigReloadCommand that = (ViewConfigReloadCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;
        if (!viewConfigExtension.equals(that.viewConfigExtension)) return false;
        if (!viewConfigRootClassName.equals(that.viewConfigRootClassName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        result = 31 * result + (viewConfigRootClassName != null ? viewConfigRootClassName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ViewConfigExtensionRefreshCommand{" +
                "classLoader=" + classLoader +
                ", resolverRootClassName='" + viewConfigRootClassName + '\'' +
                '}';
    }
}
