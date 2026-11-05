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
package org.hotswap.agent.util;

import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;

public class HotswapTransformerTest {

    private static final byte[] ORIGINAL = {0};
    private static final byte[] TRANSFORMED = {1};

    /**
     * Plugins register and remove transformers while other threads load classes, e.g. WildFly starts deployments
     * in parallel. A transformation must not be lost because of that.
     */
    @Test
    public void transformWhileTransformersChange() throws Exception {
        final HotswapTransformer hotswapTransformer = new HotswapTransformer();
        final ClassLoader classLoader = new URLClassLoader(new URL[0], null);
        // skip the class loader initialization by PluginManager
        hotswapTransformer.seenClassLoaders.put(classLoader, true);
        hotswapTransformer.registerTransformer(classLoader, "test.Stable", new TestTransformer(TRANSFORMED));

        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] writers = new Thread[4];
        for (int t = 0; t < writers.length; t++) {
            final int id = t;
            writers[t] = new Thread(() -> {
                try {
                    int i = 0;
                    while (running.get()) {
                        ClassLoader otherClassLoader = new URLClassLoader(new URL[0], null);
                        HaClassFileTransformer other = new TestTransformer(null);
                        String regexp = (i % 2 == 0) ? "test.Stable" : "test.Other" + id + "_" + (i % 50);
                        hotswapTransformer.registerTransformer(otherClassLoader, regexp, other);
                        if (i % 3 == 0) {
                            hotswapTransformer.removeTransformer(regexp, other);
                        } else {
                            hotswapTransformer.closeClassLoader(otherClassLoader);
                        }
                        i++;
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            writers[t].start();
        }

        try {
            long end = System.currentTimeMillis() + 3000;
            int lost = 0;
            int calls = 0;
            while (System.currentTimeMillis() < end) {
                byte[] result = hotswapTransformer.transform(classLoader, "test/Stable", null, null, ORIGINAL);
                calls++;
                if (!Arrays.equals(TRANSFORMED, result)) {
                    lost++;
                }
            }
            if (lost > 0) {
                throw new AssertionError("transformation lost in " + lost + " of " + calls + " calls");
            }
        } finally {
            running.set(false);
            for (Thread writer : writers) {
                writer.join();
            }
        }
        assertNull("writer failed: " + failure.get(), failure.get());
    }

    private static class TestTransformer implements HaClassFileTransformer {
        private final byte[] result;

        TestTransformer(byte[] result) {
            this.result = result;
        }

        @Override
        public boolean isForRedefinitionOnly() {
            return false;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            // the other transformers keep the bytes
            return result != null ? result : classfileBuffer;
        }
    }
}
