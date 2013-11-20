package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.PluginConfiguration;
import org.hotswap.agent.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.watch.Watcher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jiri Bubnik
 * @InitPlugin annotation handler - execute the method with custom params.
 */
public class InitHandler implements PluginHandler<Init> {
    private static AgentLogger LOGGER = AgentLogger.getLogger(InitHandler.class);

    protected PluginManager pluginManager;

    public InitHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public boolean initField(PluginAnnotation pluginAnnotation) {
        Field field = pluginAnnotation.getField();

        Object value = resolveType(pluginAnnotation.getPlugin(), pluginAnnotation.getPluginClass(), field.getType());
        field.setAccessible(true);
        try {
            field.set(pluginAnnotation.getPlugin(), value);
        } catch (IllegalAccessException e) {
            LOGGER.error("Unable to set plugin field '{}' to value '{}' on plugin '{}'",
                    e, field.getName(), value, pluginAnnotation.getPluginClass());
            return false;
        }

        return true;
    }

    // Main plugin initialization via @Init method. Method is immediately invoked.
    @Override
    public boolean initMethod(PluginAnnotation pluginAnnotation) {
        Object plugin = pluginAnnotation.getPlugin();
        List<Object> args = new ArrayList<Object>();
        for (Class type : pluginAnnotation.getMethod().getParameterTypes()) {
            args.add(resolveType(plugin, pluginAnnotation.getPluginClass(), type));
        }
        try {
            pluginAnnotation.getMethod().invoke(plugin, args.toArray());
            return true;
        } catch (IllegalAccessException e) {
            LOGGER.error("IllegalAccessException in init method on plugin '" +
                    plugin.getClass().getName() + "'.", e);
            return false;
        } catch (InvocationTargetException e) {
            LOGGER.error("InvocationTargetException in init method on plugin '" +
                    plugin.getClass().getName() + "'.", e);
            return false;
        }
    }

    protected Object resolveType(Object plugin, Class pluginClass, Class type) {

        if (type.isAssignableFrom(PluginManager.class)) {
            return pluginManager;
        } else if (type.isAssignableFrom(Watcher.class)) {
            return pluginManager.getWatcher();
        } else if (type.isAssignableFrom(HotswapTransformer.class)) {
            return pluginManager.getHotswapTransformer();
        } else if (plugin != null && type.isAssignableFrom(PluginConfiguration.class)) {
            return pluginManager.getPluginConfiguration(pluginManager.getPluginRegistry().getAppClassLoader(plugin));
        } else if (plugin != null && type.isAssignableFrom(ClassLoader.class)) {
            return pluginManager.getPluginRegistry().getAppClassLoader(plugin);
        } else {
            LOGGER.error("Unable process @Init on plugin '{}'." +
                    " Type '" + type + "' is not recognized for @Init annotation.", pluginClass);
            return null;
        }
    }
}
