package org.hotswap.agent.plugin.weld.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.WeldPlugin;

/**
 * ProxyRefreshCommand
 *
 * @author Vladimir Dvorak
 */
public class ProxyRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldPlugin.class);

    private ClassLoader classLoader;
    private String archivePath;
    private Map<Object, Object> registeredBeans;
    private String className;

    public ProxyRefreshCommand(ClassLoader classLoader, String archivePath, Map<Object, Object> registeredProxiedBeans, String className) {
        this.classLoader = classLoader;
        this.archivePath = archivePath;
        this.registeredBeans = registeredProxiedBeans;
        this.className = className;
    }

    @Override
    public void executeCommand() {
        try {
            LOGGER.debug("Executing BeanDeploymentArchiveAgent.refreshProxy('{}')", className);
            Class<?> bdaAgentClazz = Class.forName(BeanDeploymentArchiveAgent.class.getName(), true, classLoader);
            Method bdaMethod  = bdaAgentClazz.getDeclaredMethod("refreshProxy", new Class[] {ClassLoader.class, String.class, Map.class, String.class});
            bdaMethod.invoke(null, classLoader, archivePath, registeredBeans, className);
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
