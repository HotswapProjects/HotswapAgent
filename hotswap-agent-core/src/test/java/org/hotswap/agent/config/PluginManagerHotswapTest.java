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
package org.hotswap.agent.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.command.impl.SchedulerImpl;
import org.hotswap.agent.util.RedefinedClassRegistry;
import org.junit.Test;

public class PluginManagerHotswapTest {

    static class A {
    }

    static class B {
    }

    @Test
    public void rejectedClassIsRemovedFromReloadMap() throws Exception {
        final List<List<Class<?>>> redefined = new ArrayList<>();
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Instrumentation.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("redefineClasses")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    List<Class<?>> classes = new ArrayList<>();
                    for (ClassDefinition definition : (ClassDefinition[]) args[0]) {
                        classes.add(definition.getDefinitionClass());
                    }
                    redefined.add(classes);
                    if (classes.contains(A.class)) {
                        throw new UnsupportedOperationException("class redefinition failed: attempted to add a method");
                    }
                    return null;
                });
        PluginManager pluginManager = newPluginManager(instrumentation);

        Map<Class<?>, byte[]> reloadMap = new HashMap<>();
        reloadMap.put(A.class, new byte[0]);
        try {
            pluginManager.hotswap(reloadMap);
            fail("redefinition of A should fail");
        } catch (IllegalStateException expected) {
            // expected
        }
        assertTrue("rejected class stays in reload map", reloadMap.isEmpty());

        reloadMap.put(B.class, new byte[0]);
        pluginManager.hotswap(reloadMap);

        assertEquals(2, redefined.size());
        assertEquals(Collections.<Class<?>>singletonList(B.class), redefined.get(1));
        assertTrue(reloadMap.isEmpty());
    }

    /**
     * Only a successful redefinition is recorded. The JVM passes the new bytecode to the transformer before it rejects
     * it, so a class whose redefinition failed is forgotten and its class file is reloaded at the next rewrite, also
     * with the bytecode recorded before (issue #747).
     */
    @Test
    public void onlySuccessfulRedefinitionIsRecorded() throws Exception {
        final PluginManager[] holder = new PluginManager[1];
        final byte[] rejected = {2};
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Instrumentation.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("redefineClasses")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    ClassDefinition definition = ((ClassDefinition[]) args[0])[0];
                    holder[0].getHotswapTransformer().getRedefinedClasses().forget(definition.getDefinitionClass());
                    if (Arrays.equals(definition.getDefinitionClassFile(), rejected)) {
                        throw new UnsupportedOperationException("class redefinition failed: attempted to add a method");
                    }
                    return null;
                });
        PluginManager pluginManager = newPluginManager(instrumentation);
        holder[0] = pluginManager;
        RedefinedClassRegistry registry = pluginManager.getHotswapTransformer().getRedefinedClasses();
        registry.setEnabled(true);
        byte[] bytecode = {1};

        pluginManager.hotswap(new HashMap<>(Collections.<Class<?>, byte[]>singletonMap(A.class, bytecode)));
        assertTrue(registry.isCurrent(A.class, bytecode));

        try {
            pluginManager.hotswap(new HashMap<>(Collections.<Class<?>, byte[]>singletonMap(A.class, rejected)));
            fail("redefinition of A should fail");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertFalse(registry.isCurrent(A.class, rejected));
        assertFalse(registry.isCurrent(A.class, bytecode));
    }

    @Test
    public void bytecodeRejectedWithErrorIsNotRecorded() throws Exception {
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Instrumentation.class}, (proxy, method, args) -> {
                    throw new VerifyError("Bad type on operand stack");
                });
        PluginManager pluginManager = newPluginManager(instrumentation);
        RedefinedClassRegistry registry = pluginManager.getHotswapTransformer().getRedefinedClasses();
        registry.setEnabled(true);
        byte[] bytecode = {1};

        try {
            pluginManager.hotswap(new HashMap<>(Collections.<Class<?>, byte[]>singletonMap(A.class, bytecode)));
            fail("redefinition of A should fail");
        } catch (VerifyError expected) {
            // expected
        }

        assertFalse(registry.isCurrent(A.class, bytecode));
    }

    @Test
    public void emptyReloadMapIsNotRedefined() throws Exception {
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Instrumentation.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });

        newPluginManager(instrumentation).hotswap(new HashMap<>());
    }

    private static PluginManager newPluginManager(Instrumentation instrumentation) throws Exception {
        Constructor<PluginManager> constructor = PluginManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PluginManager pluginManager = constructor.newInstance();
        Field field = PluginManager.class.getDeclaredField("instrumentation");
        field.setAccessible(true);
        field.set(pluginManager, instrumentation);
        field = PluginManager.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        field.set(pluginManager, new SchedulerImpl(false));
        return pluginManager;
    }
}
