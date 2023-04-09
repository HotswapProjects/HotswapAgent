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
package org.hotswap.agent.plugin.jdk;

import static org.junit.Assert.assertTrue;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.jdk.JdkPlugin;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;

/**
 * Jdk plugin test
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Vladimir Dvorak
 */
public class JdkPluginTest {


    /**
     * Switch method implementation (using bean definition or interface).
     */
//    @Test
    public void introspectorTest() throws Exception {
        BeanInfo beanInfo1 = Introspector.getBeanInfo(TestBean1.class);
        assertTrue(containsMethod(beanInfo1, "helloWorld1"));
        swapClasses(TestBean1.class, TestBean2.class.getName());
        BeanInfo beanInfo2 = Introspector.getBeanInfo(TestBean1.class);
        assertTrue(!containsMethod(beanInfo2, "helloWorld1"));
        assertTrue(containsMethod(beanInfo2, "helloWorld2"));
        swapClasses(TestBean1.class, TestBean1.class.getName());
    }

    /**
     * Switch method implementation (using bean definition or interface).
     */
//    @Test
    public void serializationTest() throws Exception {
        Serialization1 testSerial1 = new Serialization1();
        byte bytes[] = writeObject(testSerial1);
        swapClasses(Serialization1.class, Serialization2.class.getName());
        Object testSerial2 = readObject(bytes);
    }

    private byte[] writeObject(Object o) throws IOException {
        try (ByteArrayOutputStream  bos = new ByteArrayOutputStream()) {
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(o);
            out.flush();
            return bos.toByteArray();
        }
    }

    private Object readObject(byte[] bytes) throws IOException, ClassNotFoundException {

        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        }
    }

    private boolean containsMethod(BeanInfo beanInfo, String methodName) {
        MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
        for (MethodDescriptor md: methodDescriptors) {
            if (md.getName().equals(methodName))
                return true;
        }
        return false;
    }

    private void swapClasses(Class original, String swap) throws Exception {
        JdkPlugin.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !JdkPlugin.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in weld refresh?
        Thread.sleep(100);
    }
}
