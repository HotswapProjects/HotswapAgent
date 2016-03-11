/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.hotswap.agent.plugin.proxy.test.util;

import org.hotswap.agent.plugin.proxy.HotSwapTool;
import org.hotswap.agent.plugin.proxy.MultistepProxyTransformer;
import org.hotswap.agent.plugin.proxy.ProxyPlugin;
import org.hotswap.agent.plugin.proxy.hscglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.java.JavaProxyTransformer;
import org.hotswap.agent.util.test.WaitHelper;

/**
 * Shortcut methods for testing. Methods are named this way to make them more visible in the test code.
 *
 * @author Ivan Dubrov
 */
public class HotSwapTestHelper {
    /**
     * Returns the current version of the inner classes of an outer class.
     * <p/>
     * Caller class is used as an outer class.
     *
     * @return the version of the inner classes of the outer class
     */
    public static int __version__() {
        return HotSwapTool.getCurrentVersion(determineOuter(0));
    }

    /**
     * Redefines all inner classes of a outer class to a specified version. Inner classes who do not have a particular
     * representation for a version remain unchanged.
     * <p/>
     * Caller class is used as an outer class.
     *
     * @param versionNumber
     *            the target version number
     */
    public static void __toVersion__Delayed(int versionNumber, Class<?>... extra) {
        MultistepProxyTransformer.addThirdStep = true;
        HotSwapTool.toVersion(determineOuter(0), versionNumber, extra);
        // allow time for multiple redefinitions
        WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !CglibProxyTransformer.isReloadingInProgress() && !JavaProxyTransformer.isReloadingInProgress();
            }
        });
        MultistepProxyTransformer.addThirdStep = false;
    }

    public static void __toVersion__Delayed_JavaProxy(int versionNumber, Class<?>... extra) {
        ProxyPlugin.reloadFlag = true;
        HotSwapTool.toVersion(determineOuter(0), versionNumber, extra);
        // allow time for multiple redefinitions
        WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !ProxyPlugin.reloadFlag;
            }
        });
        ProxyPlugin.reloadFlag = false;
    }

    /**
     * Redefines all inner classes of a outer class to a specified version. Inner classes who do not have a particular
     * representation for a version remain unchanged.
     * <p/>
     * Caller class is used as an outer class.
     *
     * @param versionNumber
     *            the target version number
     */
    public static void __toVersion__(int versionNumber, Class<?>... extra) {
        HotSwapTool.toVersion(determineOuter(0), versionNumber, extra);
    }

    /**
     * Helper method to determine caller outer class.
     * <p/>
     * Takes caller class and finds its top enclosing class (which is supposed to be test class).
     *
     * @param level
     *            on which level this call is being made. 0 - call is made immediately in the method of HotSwapTool.
     * @return outer class reference
     */
    private static Class<?> determineOuter(int level) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        // one for Thread#getStackTrace
        // one for #determineOuter
        // one for the caller
        String callerName = stack[level + 3].getClassName();
        try {
            Class<?> clazz = cl.loadClass(callerName);
            while (clazz.getEnclosingClass() != null) {
                clazz = clazz.getEnclosingClass();
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot find caller class: " + callerName, e);
        }
    }
}
