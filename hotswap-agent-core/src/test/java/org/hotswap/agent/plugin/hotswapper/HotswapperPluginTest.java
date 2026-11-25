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
package org.hotswap.agent.plugin.hotswapper;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.RedefinedClassRegistry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * A build tool may rewrite every class file of a module after a change of one source file. With
 * autoHotswap.skipUnchangedClasses, the classes whose bytecode did not change since autoHotswap last reloaded them
 * are not reloaded again (issue #747).
 */
public class HotswapperPluginTest {

    public static class Sample {
    }

    private final Mockery context = new Mockery();
    private final Scheduler scheduler = context.mock(Scheduler.class);
    private final ClassLoader classLoader = HotswapperPluginTest.class.getClassLoader();
    private final URL url = classLoader.getResource(Sample.class.getName().replace('.', '/') + ".class");
    private HotswapperPlugin plugin;

    @Before
    public void before() throws Exception {
        plugin = new HotswapperPlugin();
        plugin.scheduler = scheduler;
        plugin.pluginManager = PluginManager.getInstance();
        plugin.initHotswapCommand(classLoader, null);
        plugin.skipUnchangedClasses = true;
        // the class was last reloaded with the bytecode of its class file
        registry().setEnabled(true);
        registry().redefined(Collections.singletonMap(Sample.class, sample().toBytecode()));
    }

    @After
    public void after() {
        registry().setEnabled(false);
        context.assertIsSatisfied();
    }

    @Test
    public void classFileWithBytecodeOfLastReloadIsSkipped() throws Exception {
        context.checking(new Expectations() {{
            never(scheduler);
        }});

        plugin.watchReload(sample(), classLoader, url);

        assertTrue(plugin.reloadMap.isEmpty());
    }

    /**
     * A class loaded from a class directory is known from its loading: the digest of the raw class file must match
     * the bytecode javassist writes for the rewritten, identical class file.
     */
    @Test
    public void classFileWithBytecodeOfLoadedClassIsSkipped() throws Exception {
        context.checking(new Expectations() {{
            never(scheduler);
        }});
        // a class loader that defines Sample only after the digest is recorded, as the transformer does
        URL classes = Sample.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader appClassLoader = new URLClassLoader(new URL[]{classes}, null)) {
            byte[] classFile = IOUtils.toByteArray(url.toURI());
            registry().loaded(appClassLoader, Sample.class.getName().replace('.', '/'),
                    new ProtectionDomain(new CodeSource(classes, (Certificate[]) null), null), classFile);
            appClassLoader.loadClass(Sample.class.getName());

            plugin.watchReload(sample(), appClassLoader, url);

            assertTrue(plugin.reloadMap.isEmpty());
        }
    }

    /**
     * The digest recorded at loading is taken from the class file, the one compared by watchReload from the bytecode
     * javassist writes for it. Check that they are the same for all classes of this module: lambdas, inner and
     * anonymous classes, annotations, generics, switch maps, javassist itself.
     */
    @Test
    public void javassistWritesTheBytecodeOfTheClassFile() throws Exception {
        Path classes = Paths.get(HotswapperPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assumeTrue("classes of this module in a directory", Files.isDirectory(classes));
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(classes)) {
            classFiles = files.filter(file -> file.toString().endsWith(".class")).collect(Collectors.toList());
        }
        List<String> different = new ArrayList<>();
        for (Path classFile : classFiles) {
            byte[] bytecode = Files.readAllBytes(classFile);
            CtClass ctClass = new ClassPool().makeClass(new ByteArrayInputStream(bytecode));
            if (!Arrays.equals(bytecode, ctClass.toBytecode())) {
                different.add(classes.relativize(classFile).toString());
            }
        }

        assertTrue(classFiles.contains(classes.resolve(HotswapperPlugin.class.getName().replace('.', '/') + ".class")));
        assertEquals(Collections.<String>emptyList(), different);
    }

    @Test
    public void changedClassFileIsReloaded() throws Exception {
        expectReloadScheduled();
        byte[] changed = changedSample().toBytecode();

        plugin.watchReload(changedSample(), classLoader, url);

        assertEquals(Collections.singleton(Sample.class), plugin.reloadMap.keySet());
        assertArrayEquals(changed, plugin.reloadMap.get(Sample.class));
    }

    @Test
    public void pendingChangeWrittenOverWithBytecodeOfLastReloadIsDropped() throws Exception {
        expectReloadScheduled();

        plugin.watchReload(changedSample(), classLoader, url);
        plugin.watchReload(sample(), classLoader, url);

        assertTrue(plugin.reloadMap.isEmpty());
    }

    @Test
    public void unchangedClassFileIsReloadedByDefault() throws Exception {
        expectReloadScheduled();
        plugin.skipUnchangedClasses = new HotswapperPlugin().skipUnchangedClasses;

        plugin.watchReload(sample(), classLoader, url);

        assertEquals(Collections.singleton(Sample.class), plugin.reloadMap.keySet());
    }

    private static RedefinedClassRegistry registry() {
        return PluginManager.getInstance().getHotswapTransformer().getRedefinedClasses();
    }

    private void expectReloadScheduled() {
        context.checking(new Expectations() {{
            oneOf(scheduler).scheduleCommand(with(any(Command.class)), with(100),
                    with(Scheduler.DuplicateSheduleBehaviour.SKIP));
        }});
    }

    private CtClass sample() throws Exception {
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(classLoader));
        return classPool.get(Sample.class.getName());
    }

    private CtClass changedSample() throws Exception {
        CtClass ctClass = sample();
        ctClass.addField(new CtField(CtClass.intType, "added", ctClass));
        return ctClass;
    }
}
