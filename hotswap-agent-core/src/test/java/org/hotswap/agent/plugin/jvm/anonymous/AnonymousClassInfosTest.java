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
package org.hotswap.agent.plugin.jvm.anonymous;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.plugin.jvm.AnonymousClassInfo;
import org.hotswap.agent.plugin.jvm.AnonymousClassInfos;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Simulate transition after class change (add/remove inner anonymous classes).
 */
public class AnonymousClassInfosTest {

    @Test
    public void testTransitionsSameClass() throws Exception {
        AnonymousClassInfos stateInfo = getClassPoolInfos(AnonymousTestClass1.class);
        stateInfo.mapPreviousState(getClassPoolInfos(AnonymousTestClass1.class));

        Map<AnonymousClassInfo, AnonymousClassInfo> mappings = stateInfo.getCompatibleTransitions();

        for (Map.Entry<AnonymousClassInfo, AnonymousClassInfo> mapping : mappings.entrySet()) {
            assertNotNull("Class not mapped " + mapping.getKey(), mapping.getValue());
            assertTrue("Class not exact match " + mapping.getKey(), mapping.getKey().matchExact(mapping.getValue()));

            assertEquals(mapping.getKey().getClassName(), AnonymousTestClass1.class.getName() + "$1");
            assertEquals(mapping.getValue().getClassName(), AnonymousTestClass1.class.getName() + "$1");
        }
    }

    @Test
    public void testTransitionsSignatrues() throws Exception {
        AnonymousClassInfos stateInfo = getClassPoolInfos(AnonymousTestClass2.class);
        stateInfo.mapPreviousState(getClassPoolInfos(AnonymousTestClass1.class));
        Map<AnonymousClassInfo, AnonymousClassInfo> mappings = stateInfo.getCompatibleTransitions();

        for (Map.Entry<AnonymousClassInfo, AnonymousClassInfo> mapping : mappings.entrySet()) {
            assertNotNull("Class not mapped " + mapping.getKey(), mapping.getValue());

            assertEquals(mapping.getKey().getClassName(), AnonymousTestClass1.class.getName() + "$1");
            assertEquals(mapping.getValue().getClassName(), AnonymousTestClass2.class.getName() + "$1");
        }
    }

    @Test
    public void testTransitionsInsertAnonymousClass() throws Exception {
        AnonymousClassInfos stateInfo = getClassPoolInfos(AnonymousTestClass3.class);
        stateInfo.mapPreviousState(getClassPoolInfos(AnonymousTestClass2.class));

        assertEquals("Mapping class$1 -> class$2", AnonymousTestClass3.class.getName() + "$2",
                stateInfo.getCompatibleTransition(AnonymousTestClass2.class.getName() + "$1"));

    }

    @Test
    public void testTransitionsNotCompatible() throws Exception {
        AnonymousClassInfos stateInfo = getClassPoolInfos(AnonymousTestClass4.class);
        stateInfo.mapPreviousState(getClassPoolInfos(AnonymousTestClass1.class));

        assertNull("Mapping class$1 -> $hotswapAgent1", stateInfo.getCompatibleTransition(AnonymousTestClass4.class.getName() + "$1"));
    }

    @Test
    public void testTransitionsNotCompatibleRemove() throws Exception {
        AnonymousClassInfos stateInfo = getClassPoolInfos(AnonymousTestClass1.class);
        stateInfo.mapPreviousState(getClassPoolInfos(AnonymousTestClass3.class));

        assertNull("Mapping class$1 -> class$1", stateInfo.getCompatibleTransition(AnonymousTestClass1.class.getName() + "$1"));
    }


    private AnonymousClassInfos getClassPoolInfos(Class clazz) throws ClassNotFoundException {
        ClassPool classPool = new ClassPool();
        classPool.insertClassPath(new LoaderClassPath(getClass().getClassLoader()));
        return new AnonymousClassInfos(classPool, clazz.getName());
    }

    private AnonymousClassInfos getClassInfos(Class clazz) throws ClassNotFoundException {
        return new AnonymousClassInfos(getClass().getClassLoader(), clazz.getName());
    }


}
