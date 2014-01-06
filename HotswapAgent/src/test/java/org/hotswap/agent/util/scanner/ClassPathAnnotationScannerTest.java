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
