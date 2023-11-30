/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.util.classloader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.scanner.ClassPathScanner;
import org.hotswap.agent.util.scanner.Scanner;
import org.hotswap.agent.util.scanner.ScannerVisitor;

/**
 * Classloader patch which will redefine each patch via Javassist in the target classloader.
 * <p/>
 * Note that the class will typically be already accessible by parent classloader, but if it
 * is loaded from parent classloader, it does not have access to other child classloader classes.
 * <p/>
 * Redefine will work only if the class was not loaded by the child classloader. This may not be used
 * for Plugin class itself, because some target library classes may be enhanced by plugin reference
 * (e.g. to set some initialized property). Although the class resides in parent classloader it cannot
 * be redefined in child classloader with other definition - the classloader already knows about this class.
 * This is the reason, why plugin class cannot be executed in child classloader.
 *
 * @author Jiri Bubnik
 */
public class ClassLoaderDefineClassPatcher {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassLoaderDefineClassPatcher.class);

    private static Map<String, List<byte[]>> pluginClassCache = new HashMap<>();

    /**
     * Patch the classloader.
     *
     * @param classLoaderFrom  classloader to load classes from
     * @param pluginPath             path to copy
     * @param classLoaderTo    classloader to copy classes to
     * @param protectionDomain required protection in target classloader
     */
    public void patch(final ClassLoader classLoaderFrom, final String pluginPath,
                      final ClassLoader classLoaderTo, final ProtectionDomain protectionDomain) {

        List<byte[]> cache = getPluginCache(classLoaderFrom, pluginPath);

        if (cache != null) {

            final ClassPool cp = new ClassPool();
            cp.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));
            Set<String> loadedClasses = new HashSet<>();
            String packagePrefix = pluginPath.replace('/', '.');

            for (byte[] pluginBytes: cache) {
                CtClass pluginClass = null;
                try {
                    // force to load class in classLoaderFrom (it may not yet be loaded) and if the classLoaderTo
                    // is parent of classLoaderFrom, after definition in classLoaderTo will classLoaderFrom return
                    // class from parent classloader instead own definition (hence change of behaviour).
                    InputStream is = new ByteArrayInputStream(pluginBytes);
                    pluginClass = cp.makeClass(is);
                    try {
                        classLoaderFrom.loadClass(pluginClass.getName());
                    } catch (NoClassDefFoundError e) {
                        LOGGER.trace("Skipping class loading {} in classloader {} - " +
                                "class has probably unresolvable dependency.", pluginClass.getName(), classLoaderTo);
                    }
                    // and load the class in classLoaderTo as well. Now the class is defined in BOTH classloaders.
                    transferTo(pluginClass, packagePrefix, classLoaderTo, protectionDomain, loadedClasses);
                } catch (CannotCompileException e) {
                    LOGGER.trace("Skipping class definition {} in app classloader {} - " +
                            "class is probably already defined.", pluginClass.getName(), classLoaderTo);
                } catch (NoClassDefFoundError e) {
                    LOGGER.trace("Skipping class definition {} in app classloader {} - " +
                            "class has probably unresolvable dependency.", pluginClass.getName(), classLoaderTo);
                } catch (Throwable e) {
                    LOGGER.trace("Skipping class definition app classloader {} - " +
                            "unknown error.", e, classLoaderTo);
                }
            }
        }

        LOGGER.debug("Classloader {} patched with plugin classes from agent classloader {}.", classLoaderTo, classLoaderFrom);

    }

    private void transferTo(CtClass pluginClass, String pluginPath, ClassLoader classLoaderTo,
                            ProtectionDomain protectionDomain, Set<String> loadedClasses) throws CannotCompileException {
        // if the class is already loaded, skip it
        if (loadedClasses.contains(pluginClass.getName()) || pluginClass.isFrozen() ||
                !pluginClass.getName().startsWith(pluginPath)) {
            return;
        }
        // 1. interface
        try {
            if (!pluginClass.isInterface()) {
                CtClass[] ctClasses = pluginClass.getInterfaces();
                if (ctClasses != null && ctClasses.length > 0) {
                    for (CtClass ctClass : ctClasses) {
                        try {
                            transferTo(ctClass, pluginPath, classLoaderTo, protectionDomain, loadedClasses);
                        } catch (Throwable e) {
                            LOGGER.trace("Skipping class loading {} in classloader {} - " +
                                    "class has probably unresolvable dependency.", ctClass.getName(), classLoaderTo);
                        }
                    }
                }
            }
        } catch (NotFoundException e) {
        }
        // 2. superClass
        try {
            CtClass ctClass = pluginClass.getSuperclass();
            if (ctClass != null) {
                try {
                    transferTo(ctClass, pluginPath, classLoaderTo, protectionDomain, loadedClasses);
                } catch (Throwable e) {
                    LOGGER.trace("Skipping class loading {} in classloader {} - " +
                            "class has probably unresolvable dependency.", ctClass.getName(), classLoaderTo);
                }
            }
        } catch (NotFoundException e) {
        }
        pluginClass.toClass(classLoaderTo, protectionDomain);
        loadedClasses.add(pluginClass.getName());
    }

    private List<byte[]> getPluginCache(final ClassLoader classLoaderFrom, final String pluginPath) {
        List<byte[]> ret = null;
        synchronized(pluginClassCache) {
            ret = pluginClassCache.get(pluginPath);
            if (ret == null) {
                final List<byte[]> retList = new ArrayList<>();
                Scanner scanner = new ClassPathScanner();
                try {
                    scanner.scan(classLoaderFrom, pluginPath, new ScannerVisitor() {
                        @Override
                        public void visit(InputStream file) throws IOException {

                            // skip plugin classes
                            // TODO this should be skipped only in patching application classloader. To copy
                             // classes into agent classloader, Plugin class must be copied as well
    //                        if (patchClass.hasAnnotation(Plugin.class)) {
    //                            LOGGER.trace("Skipping plugin class: " + patchClass.getName());
    //                            return;
    //                        }

                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                            int readBytes;
                            byte[] data = new byte[16384];

                            while ((readBytes = file.read(data, 0, data.length)) != -1) {
                                buffer.write(data, 0, readBytes);
                            }

                            buffer.flush();
                            retList.add(buffer.toByteArray());
                        }

                    });
                } catch (IOException e) {
                    LOGGER.error("Exception while scanning 'org/hotswap/agent/plugin'", e);
                }
                ret = retList;
                pluginClassCache.put(pluginPath, ret);
            }
        }
        return ret;
    }

    /**
     * Check if the classloader can be patched.
     * Typically skip synthetic classloaders.
     *
     * @param classLoader classloader to check
     * @return if true, call patch()
     */
    public boolean isPatchAvailable(ClassLoader classLoader) {
        // we can define class in any class loader
        // exclude synthetic classloader where it does not make any sense

        // sun.reflect.DelegatingClassLoader - created automatically by JVM to optimize reflection calls
        return classLoader != null &&
                !classLoader.getClass().getName().equals("sun.reflect.DelegatingClassLoader") &&
                !classLoader.getClass().getName().equals("jdk.internal.reflect.DelegatingClassLoader")
                ;
    }
}
