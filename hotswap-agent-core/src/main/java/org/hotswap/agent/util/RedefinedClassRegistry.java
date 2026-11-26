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

import java.net.URL;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Remembers a digest of the bytecode a class is currently defined with, so that a class file rewritten with the same
 * content, e.g. by a build tool recompiling the whole module, need not be redefined again.
 * <p>
 * Nothing is recorded until {@link #setEnabled(boolean)} is called, e.g. by autoHotswap with
 * autoHotswap.skipUnchangedClasses=true. Then the classes loaded from class directories and the successful
 * redefinitions done by the agent are recorded. Any other redefinition, e.g. by a debugger or a failing one of the agent,
 * forgets the class, so the next change of its class file is always reloaded.
 * The first change of a class that is not known, e.g. one loaded from a jar or changed at loading by another Java agent
 * registered before HotswapAgent, is always reloaded too.
 * <p>
 * One registry is shared by all applications in the JVM: once one enables it, the classes of all are recorded. When a
 * debugger redefines a class at the same moment as the agent, its version may stay until the next change of the class.
 */
public class RedefinedClassRegistry {

    // class loader -> class name -> digest
    private final Map<ClassLoader, Map<String, byte[]>> digests = new WeakHashMap<>();

    // looked up once, at the start of the agent: a provider lookup while a class is defined may load provider classes
    // of the application through the same transformer
    private final MessageDigest sha256 = newSha256();

    private volatile boolean enabled;

    /**
     * Start or stop recording. When disabled, no class is current.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            synchronized (digests) {
                digests.clear();
            }
        }
    }

    /**
     * @return true if the class is known to be defined with this bytecode
     */
    public boolean isCurrent(Class<?> clazz, byte[] bytecode) {
        if (!enabled) {
            return false;
        }
        byte[] digest = digest(bytecode);
        synchronized (digests) {
            Map<String, byte[]> classes = digests.get(clazz.getClassLoader());
            return classes != null && Arrays.equals(classes.get(clazz.getName()), digest);
        }
    }

    /**
     * Record the classes the agent redefined. Call it only after the redefinition succeeded: the classes of a failed
     * one stay forgotten by {@link #redefining(Class, byte[])}.
     */
    public void redefined(Map<Class<?>, byte[]> definitions) {
        if (!enabled) {
            return;
        }
        for (Map.Entry<Class<?>, byte[]> entry : definitions.entrySet()) {
            put(entry.getKey().getClassLoader(), entry.getKey().getName(), digest(entry.getValue()));
        }
    }

    /**
     * Called for every redefinition before it is done. A recorded class redefined with other bytecode is forgotten.
     */
    public void redefining(Class<?> clazz, byte[] bytecode) {
        if (!enabled) {
            return;
        }
        byte[] digest = digest(bytecode);
        synchronized (digests) {
            Map<String, byte[]> classes = digests.get(clazz.getClassLoader());
            if (classes != null && !Arrays.equals(classes.get(clazz.getName()), digest)) {
                classes.remove(clazz.getName());
            }
        }
    }

    /**
     * Called for every class loaded, before the class is defined. Records a class loaded from a class directory.
     *
     * @param className the class name in the internal form, e.g. java/lang/String
     */
    public void loaded(ClassLoader classLoader, String className, ProtectionDomain protectionDomain, byte[] bytecode) {
        if (!enabled || classLoader == null || className == null || !isFromDirectory(protectionDomain)) {
            return;
        }
        put(classLoader, className.replace('/', '.'), digest(bytecode));
    }

    private static boolean isFromDirectory(ProtectionDomain protectionDomain) {
        CodeSource codeSource = protectionDomain != null ? protectionDomain.getCodeSource() : null;
        URL location = codeSource != null ? codeSource.getLocation() : null;
        return location != null && "file".equals(location.getProtocol()) && location.getPath().endsWith("/");
    }

    private void put(ClassLoader classLoader, String className, byte[] digest) {
        synchronized (digests) {
            digests.computeIfAbsent(classLoader, k -> new HashMap<>()).put(className, digest);
        }
    }

    private byte[] digest(byte[] bytecode) {
        synchronized (sha256) {
            return sha256.digest(bytecode);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
