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
package org.hotswap.agent.plugin.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.logging.AgentLogger;

/**
 *
 * @author Erki Ehtla
 *
 */
public class ProxyTransformationUtils {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyTransformationUtils.class);
    private static Map<ClassLoader, ClassPool> classPoolMap = new WeakHashMap<>(3);

    /**
     * Creates one ClassPool per ClassLoader and caches it
     *
     * @param classLoader
     * @return
     */
    public static ClassPool getClassPool(ClassLoader classLoader) {
        ClassPool classPool = classPoolMap.get(classLoader);
        if (classPool == null) {
            synchronized (classPoolMap) {
                classPool = classPoolMap.get(classLoader);
                if (classPool == null) {
                    classPool = createClassPool(classLoader);
                    classPoolMap.put(classLoader, classPool);
                }
            }
        }
        return classPool;
    }

    /**
     * Creates a ClassPool with supplied ClassLoader
     *
     * @param classLoader
     * @return
     */
    public static ClassPool createClassPool(final ClassLoader classLoader) {
        ClassPool cp = new ClassPool() {
            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }
        };
        cp.appendSystemPath();
        if (classLoader != null) {
            LOGGER.trace("Adding loader classpath " + classLoader);
            cp.appendClassPath(new LoaderClassPath(classLoader));
        }
        return cp;
    }

    private static final int BUFFER_SIZE = 8192;

    public static byte[] copyToByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = -1;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            return out.toByteArray();
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
            }
            try {
                out.close();
            } catch (IOException ex) {
            }
        }
    }
}
