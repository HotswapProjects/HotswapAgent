/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

package org.hotswap.agent.plugin.hotswapper;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;

import java.io.IOException;
import java.util.*;

class Trigger {
    void doSwap() {
    }
}

/**
 * Example HotSwapperJpda class from javaassist is copied to the plugin, because it needs to reside
 * in the application classloader to avoid NoClassDefFound error on tools.jar classes. Otherwise
 * it is the same code as in javassist.
 * <p/>
 * A utility class for dynamically reloading a class by
 * the Java Platform Debugger Architecture (JPDA), or <it>HotSwap</code>.
 * It works only with JDK 1.4 and later.
 * <p/>
 * <p><b>Note:</b> The new definition of the reloaded class must declare
 * the same set of methods and fields as the original definition.  The
 * schema change between the original and new definitions is not allowed
 * by the JPDA.
 * <p/>
 * <p>To use this class, the JVM must be launched with the following
 * command line options:
 * <p/>
 * <ul>
 * <p>For Java 1.4,<br>
 * <pre>java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000</pre>
 * <p>For Java 5,<br>
 * <pre>java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000</pre>
 * </ul>
 * <p/>
 * <p>Note that 8000 is the port number used by <code>HotSwapperJpda</code>.
 * Any port number can be specified.  Since <code>HotSwapperJpda</code> does not
 * launch another JVM for running a target application, this port number
 * is used only for inter-thread communication.
 * <p/>
 * <p>Furthermore, <code>JAVA_HOME/lib/tools.jar</code> must be included
 * in the class path.
 * <p/>
 * <p>Using <code>HotSwapperJpda</code> is easy.  See the following example:
 * <p/>
 * <ul><pre>
 * CtClass clazz = ...
 * byte[] classFile = clazz.toBytecode();
 * HotSwapperJpda hs = new HotSwapperJpda(8000);  // 8000 is a port number.
 * hs.reload("Test", classFile);
 * </pre></ul>
 * <p/>
 * <p><code>reload()</code>
 * first unload the <code>Test</code> class and load a new version of
 * the <code>Test</code> class.
 * <code>classFile</code> is a byte array containing the new contents of
 * the class file for the <code>Test</code> class.  The developers can
 * repatedly call <code>reload()</code> on the same <code>HotSwapperJpda</code>
 * object so that they can reload a number of classes.
 *
 * @since 3.1
 */
public class HotSwapperJpda {
    private VirtualMachine jvm;
    private MethodEntryRequest request;
    private Map<ReferenceType,byte[]> newClassFiles;

    private Trigger trigger;

    private static final String HOST_NAME = "localhost";
    private static final String TRIGGER_NAME = Trigger.class.getName();

    /**
     * Connects to the JVM.
     *
     * @param port the port number used for the connection to the JVM.
     */
    public HotSwapperJpda(int port)
            throws IOException, IllegalConnectorArgumentsException {
        this(Integer.toString(port));
    }

    /**
     * Connects to the JVM.
     *
     * @param port the port number used for the connection to the JVM.
     */
    public HotSwapperJpda(String port)
            throws IOException, IllegalConnectorArgumentsException {
        jvm = null;
        request = null;
        newClassFiles = null;
        trigger = new Trigger();
        AttachingConnector connector
            = (AttachingConnector)findConnector("com.sun.jdi.SocketAttach");

        Map<String,Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(HOST_NAME);
        arguments.get("port").setValue(port);
        jvm = connector.attach(arguments);
        EventRequestManager manager = jvm.eventRequestManager();
        request = methodEntryRequests(manager, TRIGGER_NAME);
    }

    private Connector findConnector(String connector) throws IOException {
        List<Connector> connectors = Bootstrap.virtualMachineManager().allConnectors();

        for (Connector con:connectors)
            if (con.name().equals(connector))
                return con;

        throw new IOException("Not found: " + connector);
    }

    private static MethodEntryRequest methodEntryRequests(
            EventRequestManager manager,
            String classpattern) {
        MethodEntryRequest mereq = manager.createMethodEntryRequest();
        mereq.addClassFilter(classpattern);
        mereq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        return mereq;
    }

    /* Stops triggering a hotswapper when reload() is called.
     */
    @SuppressWarnings("unused")
    private void deleteEventRequest(EventRequestManager manager,
                                    MethodEntryRequest request) {
        manager.deleteEventRequest(request);
    }

    /**
     * Reloads a class.
     *
     * @param className the fully-qualified class name.
     * @param classFile the contents of the class file.
     */
    public void reload(String className, byte[] classFile) {
        ReferenceType classtype = toRefType(className);
        Map<ReferenceType,byte[]> map = new HashMap<ReferenceType,byte[]>();
        map.put(classtype, classFile);
        reload2(map, className);
    }

    /**
     * Reloads a class.
     *
     * @param classFiles a map between fully-qualified class names
     *                   and class files.  The type of the class names
     *                   is <code>String</code> and the type of the
     *                   class files is <code>byte[]</code>.
     */
    public void reload(Map<String,byte[]> classFiles) {
        Map<ReferenceType,byte[]> map = new HashMap<ReferenceType,byte[]>();
        String className = null;
        for (Map.Entry<String,byte[]> e:classFiles.entrySet()) {
            className = e.getKey();
            map.put(toRefType(className), e.getValue());
        }

        if (className != null)
            reload2(map, className + " etc.");
    }

    private ReferenceType toRefType(String className) {
        List<ReferenceType> list = jvm.classesByName(className);
        if (list == null || list.isEmpty())
            throw new RuntimeException("no such class: " + className);
        return list.get(0);
    }

    private void reload2(Map<ReferenceType,byte[]> map, String msg) {
        synchronized (trigger) {
            startDaemon();
            newClassFiles = map;
            request.enable();
            trigger.doSwap();
            request.disable();
            Map<ReferenceType,byte[]> ncf = newClassFiles;
            if (ncf != null) {
                newClassFiles = null;
                throw new RuntimeException("failed to reload: " + msg);
            }
        }
    }

    private void startDaemon() {
        new Thread() {
            private void errorMsg(Throwable e) {
                System.err.print("Exception in thread \"HotSwap\" ");
                e.printStackTrace(System.err);
            }

            @Override
            public void run() {
                EventSet events = null;
                try {
                    events = waitEvent();
                    EventIterator iter = events.eventIterator();
                    while (iter.hasNext()) {
                        Event event = iter.nextEvent();
                        if (event instanceof MethodEntryEvent) {
                            hotswap();
                            break;
                        }
                    }
                } catch (Throwable e) {
                    errorMsg(e);
                }
                try {
                    if (events != null)
                        events.resume();
                } catch (Throwable e) {
                    errorMsg(e);
                }
            }
        }.start();
    }

    EventSet waitEvent() throws InterruptedException {
        EventQueue queue = jvm.eventQueue();
        return queue.remove();
    }

    void hotswap() {
        Map<ReferenceType,byte[]> map = newClassFiles;
        jvm.redefineClasses(map);
        newClassFiles = null;
    }

    /**
     * Swap class definition from another class file.
     * <p/>
     * This is mainly useful for unit testing - declare multiple version of a class and then
     * hotswap definition and do the tests.
     *
     * @param original original class currently in use
     * @param swap     fully qualified class name of class to swap
     * @throws Exception swap exception
     */
    public void swapClasses(Class original, String swap) throws Exception {
        // need to recreate classpool on each swap to avoid stale class definition
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(original.getClassLoader()));

        CtClass ctClass = classPool.getAndRename(swap, original.getName());

        reload(original.getName(), ctClass.toBytecode());
    }
}
