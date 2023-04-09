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

/**
 * 1 -> 4 not compatible anonymous class change.
 * Should replace $1 with empty implementation and define brand new class with code from $1.
 */
public class AnonymousTestClass4 {
    public String enclosing1() {
        AnonymousTestInterface2 interface2 = new AnonymousTestInterface2() {
            @Override
            public String test2() {
                return "enclosing2: AnonymousTestClass1.AnonymousTestInterface1.test2()";
            }
        };
        return interface2.test2();
    }
}
