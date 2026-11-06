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
package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.config.PluginRegistry;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.Watcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WatchHandlerTest {

    @Plugin(name = "ClassFilePlugin", testedVersions = "")
    public static class ClassFilePlugin {
        @OnClassFileEvent(classNameRegexp = ".*")
        public void onClassFile() {
        }
    }

    @Plugin(name = "ResourcePlugin", testedVersions = "")
    public static class ResourcePlugin {
        @OnResourceFileEvent(path = "META-INF//")
        public void onResourceWithTrailingSlash() {
        }

        @OnResourceFileEvent(path = "/META-INF")
        public void onResourceWithLeadingSlash() {
        }

        @OnResourceFileEvent(path = "META-INF")
        public void onResource() {
        }

        @OnResourceFileEvent(path = "printer.properties")
        public void onResourceFile() {
        }
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    /**
     * extraClasspath may be added to the class loader after its plugins were initialized (Tomcat plugin),
     * so it must be watched even when the class loader does not return it yet.
     */
    @Test
    public void watchesExtraClasspathNotKnownToClassLoader() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        List<File> watched = registerClassFileListener(urlClassLoader(classes), config(urls(extra), urls(), true));

        assertTrue(watched.contains(classes.getCanonicalFile()));
        assertTrue(watched.contains(extra.getCanonicalFile()));
    }

    @Test
    public void watchesExtraClasspathKnownToClassLoaderOnce() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        List<File> watched = registerClassFileListener(urlClassLoader(classes, extra), config(urls(extra), urls(), true));

        assertEquals(1, Collections.frequency(watched, extra.getCanonicalFile()));
        assertTrue(watched.contains(classes.getCanonicalFile()));
    }

    @Test
    public void watchesDirectoryOfParentAndChildClassLoaderOnce() throws Exception {
        File classes = folder.newFolder("classes");
        URLClassLoader classLoader = new URLClassLoader(urls(classes), urlClassLoader(classes));
        List<File> watched = registerClassFileListener(classLoader, config(urls(), urls(), false));

        assertEquals(Collections.singletonList(classes.getCanonicalFile()), watched);
    }

    @Test
    public void watchesExtraClasspathReachedBySymbolicLinkOnce() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        Path link = folder.getRoot().toPath().resolve("extra-link");
        try {
            Files.createSymbolicLink(link, extra.toPath());
        } catch (UnsupportedOperationException | IOException e) {
            Assume.assumeNoException("Symbolic links are not supported", e);
        }
        List<File> watched = registerClassFileListener(urlClassLoader(classes, extra),
                config(urls(link.toFile()), urls(), true));

        assertEquals(2, watched.size());
        assertEquals(1, Collections.frequency(watched, extra.getCanonicalFile()));
    }

    /**
     * extraClasspath was not added to the class loader (excluded class loader, no 'ucp' field or the field is not
     * accessible), so its plugins must not watch it.
     */
    @Test
    public void skipsExtraClasspathNotAddedToClassLoader() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        List<File> watched = registerClassFileListener(urlClassLoader(classes), config(urls(extra), urls(), false));

        assertEquals(Collections.singletonList(classes.getCanonicalFile()), watched);
    }

    @Test
    public void skipsExtraClasspathNotLocalFile() throws Exception {
        File classes = folder.newFolder("classes");
        URL[] extraClasspath = {new URL("http://localhost/classes/"),
                new URL("jar:" + folder.newFile("lib.jar").toURI() + "!/")};
        List<File> watched = registerClassFileListener(urlClassLoader(classes), config(extraClasspath, urls(), true));

        assertEquals(Collections.singletonList(classes.getCanonicalFile()), watched);
    }

    @Test
    public void skipsJarInExtraClasspath() throws Exception {
        File classes = folder.newFolder("classes");
        File jar = folder.newFile("extra.jar");
        List<File> watched = registerClassFileListener(urlClassLoader(classes), config(urls(jar), urls(), true));

        assertEquals(Collections.singletonList(classes.getCanonicalFile()), watched);
    }

    @Test
    public void watchesClasspathWithoutPluginConfiguration() throws Exception {
        File classes = folder.newFolder("classes");
        List<File> watched = registerClassFileListener(urlClassLoader(classes), null);

        assertEquals(Collections.singletonList(classes.getCanonicalFile()), watched);
    }

    @Test
    public void watchesPathWithTrailingSlash() throws Exception {
        File classes = folder.newFolder("classes");
        File metaInf = new File(classes, "META-INF");
        assertTrue(metaInf.mkdir());
        List<File> watched = registerResourceListener("onResourceWithTrailingSlash", urlClassLoader(classes),
                config(urls(), urls(), false));

        assertEquals(Collections.singletonList(metaInf.getCanonicalFile()), watched);
    }

    @Test
    public void watchesPathWithLeadingSlashInExtraClasspath() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        File metaInf = new File(extra, "META-INF");
        assertTrue(metaInf.mkdir());
        List<File> watched = registerResourceListener("onResourceWithLeadingSlash", urlClassLoader(classes),
                config(urls(extra), urls(), true));

        assertEquals(Collections.singletonList(metaInf.getCanonicalFile()), watched);
    }

    @Test
    public void watchesFileInExtraClasspath() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        File properties = new File(extra, "printer.properties");
        assertTrue(properties.createNewFile());
        List<File> watched = registerResourceListener("onResourceFile", urlClassLoader(classes),
                config(urls(extra), urls(), true));

        assertEquals(Collections.singletonList(properties.getCanonicalFile()), watched);
    }

    @Test
    public void watchesDirectoryInExtraClasspathAndWatchResourcesOnce() throws Exception {
        File classes = folder.newFolder("classes");
        File extra = folder.newFolder("extra");
        File metaInf = new File(extra, "META-INF");
        assertTrue(metaInf.mkdir());
        List<File> watched = registerResourceListener("onResource", urlClassLoader(classes),
                config(urls(extra), urls(extra), true));

        assertEquals(Collections.singletonList(metaInf.getCanonicalFile()), watched);
    }

    private static URLClassLoader urlClassLoader(File... dirs) throws Exception {
        return new URLClassLoader(urls(dirs), null);
    }

    private static URL[] urls(File... dirs) throws Exception {
        URL[] urls = new URL[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            urls[i] = dirs[i].toURI().toURL();
        }
        return urls;
    }

    private PluginConfiguration config(final URL[] extraClasspath, final URL[] watchResources,
                                       final boolean extraClasspathApplied) {
        final PluginConfiguration pluginConfiguration = context.mock(PluginConfiguration.class);
        context.checking(new Expectations() {{
            allowing(pluginConfiguration).getExtraClasspath(); will(returnValue(extraClasspath));
            allowing(pluginConfiguration).getWatchResources(); will(returnValue(watchResources));
            allowing(pluginConfiguration).isExtraClasspathApplied(); will(returnValue(extraClasspathApplied));
        }});
        return pluginConfiguration;
    }

    private List<File> registerClassFileListener(ClassLoader classLoader, PluginConfiguration pluginConfiguration)
            throws Exception {
        Method method = ClassFilePlugin.class.getMethod("onClassFile");
        return register(classLoader, pluginConfiguration, new ClassFilePlugin(), method,
                method.getAnnotation(OnClassFileEvent.class));
    }

    private List<File> registerResourceListener(String methodName, ClassLoader classLoader,
                                                PluginConfiguration pluginConfiguration) throws Exception {
        Method method = ResourcePlugin.class.getMethod(methodName);
        return register(classLoader, pluginConfiguration, new ResourcePlugin(), method,
                method.getAnnotation(OnResourceFileEvent.class));
    }

    private <T extends Annotation> List<File> register(final ClassLoader classLoader,
                                                       final PluginConfiguration pluginConfiguration,
                                                       final Object plugin, Method method, T annotation) throws Exception {
        final PluginManager pluginManager = context.mock(PluginManager.class);
        final PluginRegistry pluginRegistry = context.mock(PluginRegistry.class);
        final Watcher watcher = context.mock(Watcher.class);
        final List<File> watched = new ArrayList<>();

        context.checking(new Expectations() {{
            allowing(pluginManager).getPluginRegistry(); will(returnValue(pluginRegistry));
            allowing(pluginRegistry).getAppClassLoader(plugin); will(returnValue(classLoader));
            allowing(pluginManager).getPluginConfiguration(classLoader); will(returnValue(pluginConfiguration));
            allowing(pluginManager).getWatcher(); will(returnValue(watcher));
            allowing(watcher).addEventListener(with(same(classLoader)), with(any(URI.class)), with(any(WatchEventListener.class)));
            will(new CustomAction("collect watched path") {
                @Override
                public Object invoke(Invocation invocation) throws Throwable {
                    watched.add(Paths.get((URI) invocation.getParameter(1)).toFile().getCanonicalFile());
                    return null;
                }
            });
        }});

        @SuppressWarnings({"unchecked", "rawtypes"})
        PluginAnnotation<T> pluginAnnotation = new PluginAnnotation(plugin.getClass(), plugin, annotation, method);
        assertTrue(new WatchHandler<T>(pluginManager).initMethod(pluginAnnotation));
        context.assertIsSatisfied();
        return watched;
    }
}
