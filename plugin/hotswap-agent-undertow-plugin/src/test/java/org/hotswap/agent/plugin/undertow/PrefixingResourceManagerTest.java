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
package org.hotswap.agent.plugin.undertow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;

/**
 * See https://github.com/HotswapProjects/HotswapAgent/issues/684
 */
public class PrefixingResourceManagerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void rootIsResolvedFromDeployment() throws IOException {
        File app = newDir("app", "page.xhtml");
        File moduleA = newDir("module-a", "a.xhtml");
        File moduleB = newDir("module-b", "b.xhtml");

        PrefixingResourceManager rm = new PrefixingResourceManager(fileResourceManager(app));
        rm.setExtraResources(Arrays.asList(moduleA, moduleB));

        assertEquals(dirUrl(app), rm.getResource("/").getUrl().toExternalForm());
        assertEquals(dirUrl(app), rm.getResource("").getUrl().toExternalForm());
    }

    @Test
    public void extraResourcesStillOverrideDeploymentFiles() throws IOException {
        File app = newDir("app", "page.xhtml", "only-in-app.xhtml");
        File moduleA = newDir("module-a", "page.xhtml");
        File moduleB = newDir("module-b", "b.xhtml");

        PrefixingResourceManager rm = new PrefixingResourceManager(fileResourceManager(app));
        rm.setExtraResources(Arrays.asList(moduleA, moduleB));

        assertEquals(new File(moduleA, "page.xhtml").getCanonicalFile(), file(rm.getResource("/page.xhtml")));
        assertEquals(new File(moduleB, "b.xhtml").getCanonicalFile(), file(rm.getResource("/b.xhtml")));
        assertEquals(new File(app, "only-in-app.xhtml").getCanonicalFile(), file(rm.getResource("/only-in-app.xhtml")));
    }

    private File newDir(String name, String... files) throws IOException {
        File dir = tmp.newFolder(name);
        for (String f : files) {
            Files.write(new File(dir, f).toPath(), "<html/>".getBytes("UTF-8"));
        }
        return dir;
    }

    private static FileResourceManager fileResourceManager(File dir) throws IOException {
        return new FileResourceManager(dir.getCanonicalFile(), 1024, true, false, "/");
    }

    private static String dirUrl(File dir) throws IOException {
        return dir.getCanonicalFile().toURI().toURL().toExternalForm();
    }

    private static File file(Resource resource) throws IOException {
        assertNotNull(resource);
        return resource.getFile().getCanonicalFile();
    }
}
