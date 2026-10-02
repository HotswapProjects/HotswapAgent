/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.annotation.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.junit.Test;

public class PluginClassFileTransformerTest {

    /**
     * The context class loader is unrelated to the class being transformed, and looking anything up through it
     * while the JVM holds the defining loader's lock is what deadlocked application server boots.
     */
    @Test
    public void classPoolDoesNotConsultTheContextClassLoader() throws Exception {
        AtomicInteger contextLookups = new AtomicInteger();
        ClassLoader context = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                contextLookups.incrementAndGet();
                return null;
            }
        };
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(context);
        try {
            ClassPool classPool = PluginClassFileTransformer.createClassPool(getClass().getClassLoader());

            assertNotNull(classPool.get(getClass().getName()));
            assertNotNull(classPool.get(PluginManager.class.getName()));
            assertNotNull(classPool.get(String.class.getName()));
            assertEquals(0, contextLookups.get());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    public void classPoolForTheBootstrapLoaderStillSeesTheAgent() throws Exception {
        ClassPool classPool = PluginClassFileTransformer.createClassPool(null);

        assertNotNull(classPool.get(PluginManager.class.getName()));
        assertNotNull(classPool.get(String.class.getName()));
    }
}
