package org.hotswap.agent;

/**
 * Listener after plugin is initialized.
 *
 * @author Jiri Bubnik
 */
public interface PluginInitializedListener {
    void pluginIntialized(Object plugin, ClassLoader classLoader);
}
