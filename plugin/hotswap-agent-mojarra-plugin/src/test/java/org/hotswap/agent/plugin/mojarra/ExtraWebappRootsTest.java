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
package org.hotswap.agent.plugin.mojarra;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * See https://github.com/HotswapProjects/HotswapAgent/issues/684
 */
public class ExtraWebappRootsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File moduleA;
    private File moduleB;
    private File watched;
    private File classes;
    private File outside;

    @Before
    public void setUp() throws IOException {
        moduleA = tmp.newFolder("module-a");
        moduleB = tmp.newFolder("module-b");
        watched = tmp.newFolder("watched");
        classes = tmp.newFolder("classes");
        outside = tmp.newFolder("module-ab");
    }

    @Test
    public void resolvesConfiguredDirectories() throws IOException {
        String[] roots = ExtraWebappRoots.resolve(classLoaderWith(
                "webappDir=" + moduleA + ";" + moduleB + ";" + new File(tmp.getRoot(), "missing") + "\n"
                + "watchResources=" + watched + "\n"
                + "extraClasspath=" + classes + "\n"));

        assertArrayEquals(new String[] { root(moduleA), root(moduleB), root(watched), root(classes) }, roots);
    }

    @Test
    public void resolvesNothingWithoutConfiguration() throws IOException {
        assertEquals(0, ExtraWebappRoots.resolve(classLoaderWith("")).length);
    }

    @Test
    public void containsOnlyFilesInsideRoots() throws IOException {
        String[] roots = { root(moduleA), root(moduleB) };

        assertTrue(ExtraWebappRoots.contains(roots, url(new File(moduleA, "pages/a.xhtml"))));
        assertTrue(ExtraWebappRoots.contains(roots, url(new File(moduleB, "b.xhtml"))));

        // same name prefix, but a different directory
        assertFalse(ExtraWebappRoots.contains(roots, url(new File(outside, "x.xhtml"))));
        // escaping the root with ..
        assertFalse(ExtraWebappRoots.contains(roots, new URL(url(moduleA), "../module-ab/x.xhtml")));
        assertFalse(ExtraWebappRoots.contains(roots, new URL("http://example.com" + moduleA + "/a.xhtml")));
        assertFalse(ExtraWebappRoots.contains(new String[0], url(new File(moduleA, "a.xhtml"))));
        assertFalse(ExtraWebappRoots.contains(null, url(new File(moduleA, "a.xhtml"))));
        assertFalse(ExtraWebappRoots.contains(roots, null));
    }

    @Test
    public void webappPathIsRelativeToRoot() throws IOException {
        String[] roots = { root(moduleA), root(moduleB) };

        assertEquals("contracts/c1/template.xhtml",
                ExtraWebappRoots.webappPath(roots, url(new File(moduleB, "contracts/c1/template.xhtml"))));
        assertEquals("a.xhtml", ExtraWebappRoots.webappPath(roots, url(new File(moduleA, "a.xhtml"))));
        assertNull(ExtraWebappRoots.webappPath(roots, url(new File(outside, "x.xhtml"))));
        assertNull(ExtraWebappRoots.webappPath(roots, new URL(url(moduleA), "../module-ab/x.xhtml")));
        assertNull(ExtraWebappRoots.webappPath(null, url(new File(moduleA, "a.xhtml"))));
    }

    @Test
    public void webappPathUsesMostSpecificRoot() throws IOException {
        // e.g. extraClasspath=target/classes and webappDir=target/classes/META-INF/resources
        File resources = new File(classes, "META-INF/resources");
        assertTrue(resources.mkdirs());
        String[] roots = { root(classes), root(resources) };

        assertEquals("contracts/c1/template.xhtml",
                ExtraWebappRoots.webappPath(roots, url(new File(resources, "contracts/c1/template.xhtml"))));
        assertEquals("other/x.xhtml", ExtraWebappRoots.webappPath(roots, url(new File(classes, "other/x.xhtml"))));
    }

    private ClassLoader classLoaderWith(String properties) throws IOException {
        File dir = tmp.newFolder();
        Files.write(new File(dir, "hotswap-agent.properties").toPath(), properties.getBytes(StandardCharsets.UTF_8));
        // no parent: only this hotswap-agent.properties must be visible
        return new URLClassLoader(new URL[] { dir.toURI().toURL() }, null);
    }

    private static String root(File dir) throws IOException {
        return dir.getCanonicalPath() + File.separator;
    }

    private static URL url(File file) throws IOException {
        return file.toURI().toURL();
    }
}
