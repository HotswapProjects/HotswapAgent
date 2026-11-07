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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static PluginManager newPluginManager(Instrumentation instrumentation) throws Exception {
        Constructor<PluginManager> constructor = PluginManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PluginManager pluginManager = constructor.newInstance();
        Field field = PluginManager.class.getDeclaredField("instrumentation");
        field.setAccessible(true);
        field.set(pluginManager, instrumentation);
        return pluginManager;
    }
}
