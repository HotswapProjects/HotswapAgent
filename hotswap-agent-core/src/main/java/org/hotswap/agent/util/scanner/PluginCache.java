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
package org.hotswap.agent.util.scanner;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Jiri Bubnik
 * @deprecated not used, probably remove in favor of PluginRegistry
 */
@Deprecated
public class PluginCache {

    public static final String PLUGIN_PATH = "org/hotswap/agent/plugin";

    private Map<ClassLoader, Set<CtClass>> pluginDefs = new HashMap<>();

    Scanner scanner = new ClassPathScanner();

    public Set<CtClass> getPlugins(ClassLoader classLoader) {
        if (pluginDefs.containsKey(classLoader))
            return pluginDefs.get(classLoader);
        else
            return Collections.emptySet();
    }

    public Set<CtClass> scanPlugins(ClassLoader classLoader) throws IOException {
        if (!pluginDefs.containsKey(classLoader)) {
            synchronized (pluginDefs) {
                if (!pluginDefs.containsKey(classLoader)) {
                    final Set<CtClass> plugins = new HashSet<>();
                    final ClassPool classPool = ClassPool.getDefault();

                    scanner.scan(getClass().getClassLoader(), PLUGIN_PATH, new ScannerVisitor() {
                        @Override
                        public void visit(InputStream file) throws IOException {
                            plugins.add(classPool.makeClass(file));
                        }
                    });
                }
            }
        }

        return pluginDefs.get(classLoader);
    }
}
