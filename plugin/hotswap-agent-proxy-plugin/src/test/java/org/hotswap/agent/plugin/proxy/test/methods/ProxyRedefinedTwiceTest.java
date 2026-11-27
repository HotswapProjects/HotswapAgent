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
package org.hotswap.agent.plugin.proxy.test.methods;

import static org.hotswap.agent.plugin.proxy.test.util.HotSwapTestHelper.__toVersion__;
import static org.hotswap.agent.plugin.proxy.test.util.HotSwapTestHelper.__toVersion__Delayed_JavaProxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.ProtectionDomain;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.util.HaClassFileTransformer;
import org.hotswap.agent.util.HotswapTransformer;

import org.junit.Test;

/**
 * The reload of a proxy by the plugin must not be taken for a redefinition of its interface, and the other way round.
 */
public class ProxyRedefinedTwiceTest {

    public static class Handler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return method.getName();
        }
    }

    public interface A {
        String first();
    }

    public interface A___0 {
        String first();
    }

    // the same methods
    public interface A___1 {
        String first();
    }

    public interface A___2 {
        String first();

        String second();
    }

    public interface B {
        void same() throws IOException;
    }

    public interface B___0 {
        void same() throws IOException;
    }

    public interface B___1 {
        void same() throws IOException;
    }

    public interface C {
        void same() throws TimeoutException;
    }

    @Test
    public void methodAddedAfterRedefinitionWithoutChange() throws Exception {
        // a previous run leaves the class file of A in version 2
        __toVersion__(0);
        A a = (A) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {A.class}, new Handler());
        assertEquals("first", a.first());

        // false when the reload command of the proxy was executed
        assertFalse(__toVersion__Delayed_JavaProxy(1));
        assertEquals("first", a.first());

        __toVersion__Delayed_JavaProxy(2);
        assertEquals("second", a.getClass().getMethod("second").invoke(a));
    }

    /**
     * A proxy of two interfaces with the same method and other exceptions never implements both of them, so the
     * plugin generates it again after every redefinition. That reload must not start the next one, see
     * https://github.com/HotswapProjects/HotswapAgent/issues/619
     */
    @Test
    public void proxyReloadedByThePluginIsNotReloadedAgain() throws Exception {
        __toVersion__(0);
        final Class<?> proxyClass = Proxy.getProxyClass(getClass().getClassLoader(), B.class, C.class);
        final AtomicInteger redefinitions = new AtomicInteger();
        HaClassFileTransformer counter = new HaClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (classBeingRedefined == proxyClass) {
                    redefinitions.incrementAndGet();
                }
                return classfileBuffer;
            }

            @Override
            public boolean isForRedefinitionOnly() {
                return true;
            }
        };
        HotswapTransformer hotswapTransformer = PluginManager.getInstance().getHotswapTransformer();
        hotswapTransformer.registerTransformer(getClass().getClassLoader(), ".*\\$Proxy.*", counter);
        try {
            assertFalse(__toVersion__Delayed_JavaProxy(1));
            Thread.sleep(500);
            // with its interface and by the plugin
            assertEquals(2, redefinitions.get());
        } finally {
            hotswapTransformer.removeTransformer(".*\\$Proxy.*", counter);
        }
    }
}
