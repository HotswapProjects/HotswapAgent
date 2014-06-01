package org.hotswap.agent.config;

/**
 * Listener after plugin is initialized.
 *
 * @author Jiri Bubnik
 */
public interface PluginInitializedListener {
    void pluginIntialized(Object plugin, ClassLoader classLoader);
}
