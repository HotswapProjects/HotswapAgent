package org.hotswap.agent.util.classloader;

import org.hotswap.agent.config.PluginManager;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * Created by bubnik on 13.10.13.
 */
public class ClassLoaderProxyTest {
    @Test
    public void test() throws MalformedURLException, ClassNotFoundException {

        ClassLoader appClassLoader = new URLClassLoader(new URL[]{}, getClass().getClassLoader());

        assertEquals("Class created in parent classloader", getClass().getClassLoader(),
                appClassLoader.loadClass(PluginManager.class.getName()).getClassLoader());
        ;

        ClassLoader samePathClassLoader = new URLClassLoader(new URL[]{Paths.get("j:\\pokusy\\DcevmAgent\\target\\classes\\").toUri().toURL()}, getClass().getClassLoader());

        assertEquals("Class created in parent classloader", getClass().getClassLoader(),
                samePathClassLoader.loadClass(PluginManager.class.getName()).getClassLoader());
        ;

    }
}
