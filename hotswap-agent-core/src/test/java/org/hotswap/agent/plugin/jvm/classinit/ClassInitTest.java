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
package org.hotswap.agent.plugin.jvm.classinit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.jvm.ClassInitPlugin;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;

public class ClassInitTest {

//    @Test
    public void testStatic1() throws Exception {
        StaticTest1 o = new StaticTest1();
        ClassInitPlugin.reloadFlag = true;
        swapClasses(StaticTest1.class, StaticTest2.class.getName());
        assertEquals(ReflectionHelper.get(o, "int1"), StaticTest1.int1); // must have original value (10)
        assertEquals(ReflectionHelper.get(o, "int2"), StaticTest2.int2); // must have new value (20)
        assertEquals(ReflectionHelper.get(o, "int3"), StaticTest2.int3); // must have new value (20)
        assertEquals(ReflectionHelper.get(o, "str1"), StaticTest1.str1); // must have original value ("str1")
        assertEquals(ReflectionHelper.get(o, "str2"), StaticTest2.str2); // must have new value  ("str2")
        assertEquals(ReflectionHelper.get(o, "str3"), StaticTest2.str3); // must have new value  ("str2")
        assertEquals(ReflectionHelper.get(o, "obj1"), StaticTest1.obj1); // must have original value ("obj1")
        assertEquals(ReflectionHelper.get(o, "obj2"), StaticTest2.obj2); // must have new value ("obj2")
        assertEquals(ReflectionHelper.get(o, "obj3"), StaticTest2.obj3); // must have new value ("obj2")
    }

//    @Test
    public void testEnumAddRemove() throws Exception {
        Enum1 enum1 = Enum1.ITEM_1;
        ClassInitPlugin.reloadFlag = true;
        int lenEnum1 = Enum1.values().length;

        swapClasses(Enum1.class, Enum2.class.getName());

        assertEquals(enum1.values().length, Enum2.values().length);

        assertEquals(ReflectionHelper.get(enum1, "ITEM_1"), enum1); // new ITEM_1 is same instance like enum1

        boolean wasException;
        try {
            enum1.getClass().getDeclaredField("ITEM_2"); // ITEM_2 was deleted from enumeration
            wasException = false;
        } catch (NoSuchFieldException e) {
            wasException = true;
        }
        assertTrue(wasException);

        try {
            enum1.getClass().getDeclaredField("ITEM_3"); // ITEM_3 was added to enumeration
            enum1.getClass().getDeclaredField("ITEM_4"); // ITEM_4 was added to enumeration
            wasException = false;
        } catch (NoSuchFieldException e) {
            wasException = true;
        }
        assertFalse(wasException);
    }

//    @Test
    public void testEnumRemove() throws Exception {
        Enum3 enum1 = Enum3.ITEM_1;
        Enum3 enum3 = Enum3.ITEM_3;

        int lenEnum4 = Enum4.values().length;

        ClassInitPlugin.reloadFlag = true;
        swapClasses(Enum3.class, Enum4.class.getName());

        assertEquals(Enum3.values().length, lenEnum4);

        assertEquals(Enum3.values()[0], enum1); // must be kept from  original
        assertEquals(Enum3.values()[1], enum3); // must be kept and shifted
    }

    private void swapClasses(Class<?> cls1, String class2Name) throws Exception {

        ClassInitPlugin.reloadFlag = true;
        HotSwapper.swapClasses(cls1, class2Name);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !ClassInitPlugin.reloadFlag;
            }
        }));
    }
}
