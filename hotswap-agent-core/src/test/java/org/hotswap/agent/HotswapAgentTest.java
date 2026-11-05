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
package org.hotswap.agent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HotswapAgentTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * The agent can be loaded twice, e.g. by -XX:HotswapAgent=fatjar and -javaagent (#560, #600).
     * Only the first one may initialize, otherwise classes are patched twice (DuplicateMemberException).
     */
    @Test
    public void agentLoadedTwiceInitializesOnce() throws Exception {
        File agentJar = temporaryFolder.newFile("agent.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", HotswapAgent.class.getName());
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(agentJar), manifest)) {
            // agent classes are on the class path of the started JVM
        }

        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!arg.startsWith("-javaagent") && !arg.startsWith("-agentlib") && !arg.startsWith("-Xrunjdwp")) {
                command.add(arg);
            }
        }
        command.add("-javaagent:" + agentJar.getAbsolutePath());
        command.add("-javaagent:" + agentJar.getAbsolutePath());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = read(process.getInputStream());
        assertTrue("JVM did not exit", process.waitFor(60, TimeUnit.SECONDS));

        assertTrue(output, output.contains("main done"));
        assertEquals(output, 1, count(output, "Loading Hotswap agent"));
        assertEquals(output, 1, count(output, "Hotswap agent is already loaded"));
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int count(String text, String part) {
        int result = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            result++;
        }
        return result;
    }

    public static class Main {
        public static void main(String[] args) {
            System.out.println("main done");
            System.exit(0);
        }
    }
}
