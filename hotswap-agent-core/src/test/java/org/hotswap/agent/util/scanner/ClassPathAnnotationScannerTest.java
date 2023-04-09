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
package org.hotswap.agent.util.scanner;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.testData.SimplePlugin;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test scanner.
 */
public class ClassPathAnnotationScannerTest {

    @Test
    public void testScanPlugins() throws Exception {
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner(Plugin.class.getName(), new ClassPathScanner());

        assertArrayEquals("Plugin discovered", new String[]{SimplePlugin.class.getName()},
                scanner.scanPlugins(getClass().getClassLoader(), "org/hotswap/agent/testData").toArray());
    }
}
