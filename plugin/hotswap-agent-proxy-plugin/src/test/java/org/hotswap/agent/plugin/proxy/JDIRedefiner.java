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
package org.hotswap.agent.plugin.proxy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

/**
 * Utility class for performing class redefinition using JDI.</li>
 * </ul>
 *
 * @author Thomas Wuerthinger
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 *
 */
public class JDIRedefiner implements Redefiner {

    private static final String PORT_ARGUMENT_NAME = "port";
    private static final String TRANSPORT_NAME = "dt_socket";

    private VirtualMachine vm;

    /** Port at which to connect to the agent of the VM. **/
    public static final int PORT = 4000;

    public JDIRedefiner(int port) throws IOException {
        vm = connect(port);
    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    private VirtualMachine connect(int port) throws IOException {
        VirtualMachineManager manager = Bootstrap.virtualMachineManager();

        // Find appropiate connector
        List<AttachingConnector> connectors = manager.attachingConnectors();
        AttachingConnector chosenConnector = null;
        for (AttachingConnector c : connectors) {
            if (c.transport().name().equals(TRANSPORT_NAME)) {
                chosenConnector = c;
                break;
            }
        }
        if (chosenConnector == null) {
            throw new IllegalStateException("Could not find socket connector");
        }

        // Set port argument
        AttachingConnector connector = chosenConnector;
        Map<String, Argument> defaults = connector.defaultArguments();
        Argument arg = defaults.get(PORT_ARGUMENT_NAME);
        if (arg == null) {
            throw new IllegalStateException("Could not find port argument");
        }
        arg.setValue(Integer.toString(port));

        // Attach
        try {
            System.out.println("Connector arguments: " + defaults);
            return connector.attach(defaults);
        } catch (IllegalConnectorArgumentsException e) {
            throw new IllegalArgumentException("Illegal connector arguments",
                    e);
        }
    }

    public void disconnect() {
        if (vm != null) {
            vm.dispose();
            vm = null;
        }
    }

    public void redefineClasses(Map<Class<?>, byte[]> classes) {
        refreshAllClasses();
        List<ReferenceType> references = vm.allClasses();

        Map<ReferenceType, byte[]> map = new HashMap<ReferenceType, byte[]>(
                classes.size());
        for (Map.Entry<Class<?>, byte[]> entry : classes.entrySet()) {
            map.put(findReference(references, entry.getKey().getName()),
                    entry.getValue());
        }
        vm.redefineClasses(map);
    }

    /**
     * Call this method before calling allClasses() in order to refresh the JDI
     * state of loaded classes. This is necessary because the JDI map of all
     * loaded classes is only updated based on events received over JDWP
     * (network connection) and therefore it is not necessarily up-to-date with
     * the real state within the VM.
     */
    private void refreshAllClasses() {
        try {
            Field f = vm.getClass().getDeclaredField("retrievedAllTypes");
            f.setAccessible(true);
            f.set(vm, false);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE,
                    null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE,
                    null, ex);
        } catch (NoSuchFieldException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE,
                    null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }

    private static ReferenceType findReference(List<ReferenceType> list,
            String name) {
        for (ReferenceType ref : list) {
            if (ref.name().equals(name)) {
                return ref;
            }
        }
        throw new IllegalArgumentException(
                "Cannot find corresponding reference for class name '" + name
                        + "'");
    }
}
