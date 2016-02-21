package org.hotswap.agent.plugin.deltaspike;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

public class PartialBeanClassRefreshCommand extends MergeableCommand  {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PartialBeanClassRefreshCommand.class);

    ClassLoader classLoader;
    Object partialBean;
    String className;
    Object repositoryComponent;


    public PartialBeanClassRefreshCommand(ClassLoader classLoader, Object partialBean, String className) {
        this.classLoader = classLoader;
        this.partialBean = partialBean;
        this.className = className;
    }

    public void setRepositoryComponent(Object repositoryComponent) {
        this.repositoryComponent = repositoryComponent;
    }

    @Override
    public void executeCommand() {
        boolean reloaded = false;
        try {
            LOGGER.debug("Executing PartialBeanClassRefreshAgent.refreshPartialBeanClass('{}')", className);
            Class<?> agentClazz = Class.forName(PartialBeanClassRefreshAgent.class.getName(), true, classLoader);
            Method m  = agentClazz.getDeclaredMethod("refreshPartialBeanClass", new Class[] {ClassLoader.class, Object.class});
            m.invoke(null, classLoader, partialBean);
            reloaded = true;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in classLoader {}", e, className, classLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
        }
        if (reloaded) {
            if (repositoryComponent != null) {
                try {
                    Method reinitializeMethod = resolveClass("org.apache.deltaspike.data.impl.meta.RepositoryComponent").getDeclaredMethod("__reinitialize");
                    reinitializeMethod.invoke(repositoryComponent);
                } catch (Exception e) {
                    LOGGER.error("Error reinitializing repository " + className, e);
                }
            }
        }
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, classLoader);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PartialBeanClassRefreshCommand that = (PartialBeanClassRefreshCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;
        if (!partialBean.equals(that.partialBean)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        result = 31 * result + partialBean.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PartialBeanClassRefreshCommand{" +
                "classLoader=" + classLoader +
                ", partialBean='" + partialBean + '\'' +
                '}';
    }

}
