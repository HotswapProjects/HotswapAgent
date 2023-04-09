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

public class StaticTest2 {
    static int int1 = 20; // Changed from 10 -> 20
    static int int2 = 20;
    static final int int3 = 20;
    static String str1 = "str2";
    static String str2 = "str2";
    static final String str3 = "str3";
    static Object obj1 = new String("obj2");
    static Object obj2 = new String("obj2");
    static final Object obj3 = new String("obj3");
}
