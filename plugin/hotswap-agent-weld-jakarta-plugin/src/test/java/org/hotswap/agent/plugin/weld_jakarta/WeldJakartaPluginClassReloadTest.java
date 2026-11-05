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
package org.hotswap.agent.plugin.weld_jakarta;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.plugin.weld_jakarta.command.BdaAgentRegistry;
import org.hotswap.agent.plugin.weld_jakarta.testBeans.HelloServiceImpl1;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * classReload is called on every plugin instance, e.g. one per deployment in WildFly.
 */
@RunWith(WeldJUnit4Runner.class)
public class WeldJakartaPluginClassReloadTest {

    @Test
    public void classReloadOnlyForOwnClassLoader() throws Exception {
        ClassLoader classLoader = HelloServiceImpl1.class.getClassLoader();
        assertNotNull("test bean archive is registered", BdaAgentRegistry.getArchiveByClassName(HelloServiceImpl1.class.getName()));

        // plugin instance of another application
        RecordingScheduler otherScheduler = new RecordingScheduler();
        newPlugin(new URLClassLoader(new URL[0], null), otherScheduler)
                .classReload(classLoader, ctClass(classLoader), HelloServiceImpl1.class);
        assertEquals(0, otherScheduler.commands.size());

        // plugin instance of this application
        RecordingScheduler ownScheduler = new RecordingScheduler();
        newPlugin(classLoader, ownScheduler).classReload(classLoader, ctClass(classLoader), HelloServiceImpl1.class);
        assertEquals(1, ownScheduler.commands.size());
    }

    private static WeldJakartaPlugin newPlugin(ClassLoader appClassLoader, Scheduler scheduler) {
        WeldJakartaPlugin plugin = new WeldJakartaPlugin();
        plugin.appClassLoader = appClassLoader;
        plugin.scheduler = scheduler;
        return plugin;
    }

    private static CtClass ctClass(ClassLoader classLoader) throws Exception {
        ClassPool classPool = new ClassPool(true);
        classPool.appendClassPath(new LoaderClassPath(classLoader));
        return classPool.get(HelloServiceImpl1.class.getName());
    }

    private static class RecordingScheduler implements Scheduler {
        final List<Command> commands = new ArrayList<>();

        @Override
        public void scheduleCommand(Command command) {
            commands.add(command);
        }

        @Override
        public void scheduleCommand(Command command, int timeout) {
            commands.add(command);
        }

        @Override
        public void scheduleCommand(Command command, int timeout, DuplicateSheduleBehaviour behaviour) {
            commands.add(command);
        }

        @Override
        public void scheduleCommandOnClassesRedefinedOrTimeout(Command command, int timeout) {
            commands.add(command);
        }

        @Override
        public void scheduleCommandOnClassesRedefinedOrTimeout(Command command, int timeout,
                                                               DuplicateSheduleBehaviour behaviour) {
            commands.add(command);
        }

        @Override
        public void run() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void onClassesRedefined() {
        }
    }
}
