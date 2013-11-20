package org.hotswap.agent.plugin.jetty;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * FIXME not used
 *
 * @author Jiri Bubnik
 */
public class ParentLastURLClassLoader extends URLClassLoader {
    ClassLoader parent;

    public ParentLastURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.parent = parent;
    }

    /* ------------------------------------------------------------ */
    public Class loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    /* ------------------------------------------------------------ */
    protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class c = findLoadedClass(name);
        ClassNotFoundException ex = null;

        if (c == null) {
            try {
                c = this.findClass(name);
            } catch (ClassNotFoundException e) {
                ex = e;
            }
        }

        if (c == null && parent != null)
            c = parent.loadClass(name);

        if (c == null)
            throw ex;

        if (resolve)
            resolveClass(c);

        return c;
    }
}
