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
package org.hotswap.agent.plugin.proxy.test.methods;

import static org.hotswap.agent.plugin.proxy.test.util.HotSwapTestHelper.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

public class AddEnhancerMethodProxyTest {

    // Version 0
    public static class AImpl implements A {
        @Override
        public int getValue1() {
            return 1;
        }
    }

    // Version 0
    public static class AImpl___0 implements A___0 {
        @Override
        public int getValue1() {
            return 1;
        }
    }

    // Version 1
    public static class AImpl___1 implements A___1 {
        @Override
        public int getValue2() {
            return 2;
        }
    }

    // Version 2
    public static class AImpl___2 implements A___2 {
        @Override
        public int getValue3() {
            return 3;
        }
    }

    // Version 0
    public interface A {
        public int getValue1();
    }

    // Version 0
    public interface A___0 {
        public int getValue1();
    }

    // Version 1
    public interface A___1 {
        public int getValue2();
    }

    // Version 2
    public interface A___2 {
        public int getValue3();
    }

    @Before
    public void setUp() throws Exception {
        __toVersion__Delayed(0);
    }

    @Test
    public void addMethodToInterfaceAndImplementation()
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        assert __version__() == 0;

        final A a = new AImpl();

        assertEquals(1, a.getValue1());

        __toVersion__Delayed(1);

        Method method = getMethod(a, "getValue2");
        assertEquals(2, method.invoke(a, null));
    }

    public static class SerializableNoOp
            implements Serializable, MethodInterceptor {
        private int count;
        private AImpl obj = new AImpl();

        @Override
        public Object intercept(Object proxy, Method method, Object[] args,
                MethodProxy methodProxy) throws Throwable {
            if (method.getName().startsWith("getValue"))
                count++;
            return method.invoke(obj, args);
        }

        public int getInvocationCount() {
            return count;
        }
    }

    @Test
    public void accessNewMethodOnProxy() throws IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        assert __version__() == 0;

        SerializableNoOp cb = new SerializableNoOp();
        final A a = createEnhancer(cb);

        assertEquals(0, cb.getInvocationCount());
        assertEquals(1, a.getValue1());
        assertEquals(1, cb.getInvocationCount());

        __toVersion__Delayed(1);
        Method method = getMethod(a, "getValue2");
        assertEquals("getValue2", method.getName());
        assertEquals(1, cb.getInvocationCount());
        assertEquals(2, method.invoke(a, null));
        assertEquals(2, cb.getInvocationCount());

        __toVersion__Delayed(2);
        method = getMethod(a, "getValue3");
        assertEquals("getValue3", method.getName());
        assertEquals(2, cb.getInvocationCount());
        assertEquals(3, method.invoke(a, null));
        assertEquals(3, cb.getInvocationCount());
    }

    private A createEnhancer(Callback cb) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(AImpl.class);

        enhancer.setCallback(cb);
        final A a = (A) enhancer.create();
        return a;
    }

    @Test
    public void accessNewMethodOnProxyCreatedAfterSwap()
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, IOException {
        assert __version__() == 0;
        SerializableNoOp cb = new SerializableNoOp();
        A a = createEnhancer(cb);

        assertEquals(0, cb.getInvocationCount());
        assertEquals(1, a.getValue1());
        assertEquals(1, cb.getInvocationCount());
        __toVersion__Delayed(1);

        a = createEnhancer(cb);

        Method method = getMethod(a, "getValue2");
        assertEquals("getValue2", method.getName());
        assertEquals(1, cb.getInvocationCount());
        assertEquals(2, method.invoke(a, null));
        assertEquals(2, cb.getInvocationCount());
    }

    private Method getMethod(Object a, String methodName) {
        Method[] declaredMethods = a.getClass().getMethods();
        Method m = null;
        for (Method method : declaredMethods) {
            if (method.getName().equals(methodName))
                m = method;
        }
        if (m == null) {
            fail(a.getClass().getSimpleName() + " does not have method "
                    + methodName);
        }
        return m;
    }

}