package org.hotswap.agent.config;

/**
 * Register a listener on PluginManager to watch for new ClassLoader instance.
 */
public interface ClassLoaderInitListener {

    /**
     * ClassLoader is initialized.
     *
     * @param classLoader new classloader
     */
    public void onInit(ClassLoader classLoader);

}
