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
package org.hotswap.agent.util.scanner;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClassPathScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void scanJarWithoutDirectoryEntries() throws Exception {
        assertEquals(2, scanJar(false));
    }

    @Test
    public void scanJarWithDirectoryEntries() throws Exception {
        assertEquals(2, scanJar(true));
    }

    private int scanJar(boolean directoryEntries) throws Exception {
        File jar = tmp.newFile("plugins.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest)) {
            if (directoryEntries) {
                out.putNextEntry(new JarEntry("test/"));
                out.putNextEntry(new JarEntry("test/scanned/"));
                out.putNextEntry(new JarEntry("test/scanned/sub/"));
            }
            for (String name : new String[]{"test/scanned/A.class", "test/scanned/sub/B.class", "test/other/C.class"}) {
                out.putNextEntry(new JarEntry(name));
                out.write(name.getBytes());
            }
        }

        final List<String> visited = new ArrayList<>();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, null)) {
            new ClassPathScanner().scan(classLoader, "test/scanned", file -> {
                try {
                    visited.add(new String(readAll(file)));
                } finally {
                    file.close();
                }
            });
        }
        return visited.size();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
