package org.hotswap.agent.util.classloader;

import org.hotswap.agent.config.PluginManager;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;

/**
 * Created by bubnik on 14.10.13.
 */
public class ClassLoaderURLPatcherTest {

    @Test
    public void testWithoutPatch() throws Exception {
        ClassLoader appClassLoader = new URLClassLoader(new URL[]{}, getClass().getClassLoader());
        Class pluginManagerInAppClassLoaderClass = appClassLoader.loadClass(PluginManager.class.getName());

        assertEquals("Plugin Manager from parent classloader before patch", getClass().getClassLoader(),
                pluginManagerInAppClassLoaderClass.getClassLoader());

    }

    @Test
    public void testPatch() throws Exception {
        ClassLoaderURLPatcher classLoaderURLPatcher = new ClassLoaderURLPatcher();

        // create classloader without parent - simulate webapp classloader with own classpath precedence.
        ClassLoader appClassLoader = new URLClassLoader(new URL[]{}, null);

        classLoaderURLPatcher.patch(getClass().getClassLoader(), PluginManager.PLUGIN_PACKAGE.replace(".", "/"),
                appClassLoader, null);
        Class pluginManagerInAppClassLoaderClass = appClassLoader.loadClass(PluginManager.class.getName());

        assertEquals("Plugin Manager from child classloader after patch", appClassLoader,
                pluginManagerInAppClassLoaderClass.getClassLoader());


    }
}
