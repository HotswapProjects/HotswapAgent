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
import static org.junit.Assert.assertNull;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicInteger;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtNewMethod;
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
    /**
     * A class loader that does not delegate to the agent's loader, like the servlet container class loader of
     * Gretty (#635), cannot run code calling the agent. Such a transformation must be dropped instead of failing
     * later with NoClassDefFoundError.
     */
    @Test
    public void detectsAgentClassNotVisibleFromIsolatedClassLoader() throws Exception {
        byte[] original = testClass("test.Isolated1", null);
        byte[] transformed = testClass("test.Isolated1",
                "public static Object agent() { return " + PluginManager.class.getName() + ".getInstance(); }");

        ClassLoader isolated = new URLClassLoader(new URL[0], null);

        assertEquals(PluginManager.class.getName(),
                PluginClassFileTransformer.findInvisibleAgentClass(isolated, original, transformed));
    }

    /**
     * Plugin classes are copied into every class loader, so they can be loaded even where the agent core is not
     * visible, but then fail in their static initializer (Tomcat plugin in Gretty, #635).
     */
    @Test
    public void requiresAgentCoreEvenIfCopiedPluginClassIsVisible() throws Exception {
        String pluginName = "org.hotswap.agent.plugin.fake.FakePlugin";
        ClassPool classPool = PluginClassFileTransformer.createClassPool(PluginClassFileTransformerTest.class.getClassLoader());
        CtClass plugin = classPool.makeClass(pluginName);
        final byte[] pluginBytes = plugin.toBytecode();

        CtClass caller = classPool.makeClass("test.CallsPlugin1");
        byte[] original = caller.toBytecode();
        caller.defrost();
        caller.addMethod(CtNewMethod.make("public static Object plugin() { return new " + pluginName + "(); }", caller));
        byte[] transformed = caller.toBytecode();

        ClassLoader isolatedWithPlugin = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(pluginName)) {
                    return defineClass(name, pluginBytes, 0, pluginBytes.length);
                }
                throw new ClassNotFoundException(name);
            }
        };

        assertNotNull(Class.forName(pluginName, false, isolatedWithPlugin));
        assertEquals(PluginManager.class.getName(),
                PluginClassFileTransformer.findInvisibleAgentClass(isolatedWithPlugin, original, transformed));
    }

    @Test
    public void acceptsAgentClassVisibleFromClassLoader() throws Exception {
        byte[] original = testClass("test.Visible1", null);
        byte[] transformed = testClass("test.Visible1",
                "public static Object agent() { return " + PluginManager.class.getName() + ".getInstance(); }");

        assertNull(PluginClassFileTransformer.findInvisibleAgentClass(
                PluginManager.class.getClassLoader(), original, transformed));
    }

    @Test
    public void acceptsTransformationWithoutAgentReferences() throws Exception {
        byte[] original = testClass("test.NoAgent1", null);
        byte[] transformed = testClass("test.NoAgent1", "public static String plain() { return \"x\"; }");

        assertNull(PluginClassFileTransformer.findInvisibleAgentClass(
                new URLClassLoader(new URL[0], null), original, transformed));
    }

    private static byte[] testClass(String name, String method) throws Exception {
        ClassPool classPool = PluginClassFileTransformer.createClassPool(PluginClassFileTransformerTest.class.getClassLoader());
        CtClass ctClass = classPool.makeClass(name);
        if (method != null) {
            ctClass.addMethod(CtNewMethod.make(method, ctClass));
        }
        byte[] bytes = ctClass.toBytecode();
        ctClass.detach();
        return bytes;
    }
}
