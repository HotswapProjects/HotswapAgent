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
package org.hotswap.agent.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IOUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void urlToClassNameClosesClassFile() throws Exception {
        File classFile = new File(tmp.getRoot(), "IOUtilsTest.class");
        try (InputStream is = IOUtilsTest.class.getResourceAsStream("IOUtilsTest.class")) {
            Files.copy(is, classFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        assertEquals(IOUtilsTest.class.getName(), IOUtils.urlToClassName(classFile.toURI()));

        assertFalse("class file is still open", isOpenByThisProcess(classFile));
        // on Windows an open file cannot be deleted
        assertTrue("class file cannot be deleted", classFile.delete());
    }

    private static boolean isOpenByThisProcess(File file) throws IOException {
        Path fdDir = Paths.get("/proc/self/fd");
        if (!Files.isDirectory(fdDir)) {
            return false;
        }
        Path target = file.toPath().toRealPath();
        try (Stream<Path> fds = Files.list(fdDir)) {
            return fds.anyMatch(fd -> {
                try {
                    return Files.readSymbolicLink(fd).equals(target);
                } catch (IOException | UnsupportedOperationException e) {
                    return false;
                }
            });
        }
    }
}
