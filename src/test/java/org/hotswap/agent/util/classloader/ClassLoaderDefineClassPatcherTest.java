package org.hotswap.agent.util.classloader;

import org.hotswap.agent.plugin.hibernate.HibernatePlugin;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;

/**
 * Created by bubnik on 29.10.13.
 */
public class ClassLoaderDefineClassPatcherTest {
    //    @Test
    public void testWithoutPatch() throws Exception {
        ClassLoader appClassLoader = new URLClassLoader(new URL[]{}, getClass().getClassLoader());

        assertEquals("Class created in parent classloader", getClass().getClassLoader(),
                appClassLoader.loadClass(HibernatePlugin.class.getName()).getClassLoader());
        ;
    }

    @Test
    public void testPatch() throws Exception {
        ClassLoader appClassLoader = new URLClassLoader(new URL[]{}, getClass().getClassLoader());

        assertEquals("Class created in parent classloader", getClass().getClassLoader(),
                appClassLoader.loadClass(HibernatePlugin.class.getName()).getClassLoader());

        new ClassLoaderDefineClassPatcher().patch(appClassLoader, null);

        assertEquals("Class created in app classloader", appClassLoader,
                appClassLoader.loadClass(HibernatePlugin.class.getName()).getClassLoader());
        ;
    }

}
