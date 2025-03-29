/*
 * Copyright 2013-2025 the HotswapAgent authors.
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

/**
 * Insert class $2 before class $1.
 */
public class AnonymousTestClass3 {
    public String enclosing1() {
        AnonymousTestInterface2 interface2 = new AnonymousTestInterface2() {
            @Override
            public String test2() {
                return "enclosing1: AnonymousTestClass.AnonymousTestInterface2.test2()";
            }
        };
        return interface2.test2();
    }

    public String enclosing2() {
        AnonymousTestInterface1 interface1 = new AnonymousTestInterface1() {
            @Override
            public String test1() {
                return "enclosing2: AnonymousTestClass.AnonymousTestInterface1.test1()";
            }
        };
        return interface1.test1();
    }
}
