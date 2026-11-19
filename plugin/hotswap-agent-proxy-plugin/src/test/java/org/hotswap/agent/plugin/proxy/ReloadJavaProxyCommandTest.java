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
package org.hotswap.agent.plugin.proxy;

import org.junit.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReloadJavaProxyCommandTest {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Select {
        String value();
    }

    public interface Plain {
        String a();

        void b(int x) throws Exception;
    }

    public interface Annotated {
        @Select("select 1")
        List<String> selectList(@Select("id") String id);
    }

    public interface Generic<T> {
        T get();

        List<? extends T> all();
    }

    public interface WithDefaultAndStatic {
        String a();

        default String d() {
            return "d";
        }

        static String s() {
            return "s";
        }
    }

    public interface Extending extends Plain {
        String c();

        @Override
        String toString();
    }

    public interface ObjectGetter {
        Object get();
    }

    public interface StringGetter {
        String get();
    }

    public interface PlainWithNewMethod {
        String a();

        void b(int x) throws Exception;

        String added();
    }

    public interface PlainWithOtherReturnType {
        Integer a();

        void b(int x) throws Exception;
    }

    public interface PlainWithWiderReturnType {
        Object a();

        void b(int x) throws Exception;
    }

    public interface PlainWithOtherException {
        String a();

        void b(int x) throws java.io.IOException;
    }

    public interface CharSequenceGetter {
        CharSequence get();
    }

    @Test
    public void proxyImplementsItsInterfaces() {
        assertImplements(Plain.class);
        assertImplements(Annotated.class);
        assertImplements(Generic.class);
        assertImplements(WithDefaultAndStatic.class);
        assertImplements(Extending.class);
        assertImplements(ObjectGetter.class, StringGetter.class);
    }

    @Test
    public void proxyWithoutNewInterfaceMethod() {
        Class<?> proxyClass = proxyClass(Plain.class);
        assertFalse(ReloadJavaProxyCommand.implementsInterfaces(proxyClass, new Class<?>[]{PlainWithNewMethod.class}));
    }

    @Test
    public void proxyWithOtherReturnType() {
        Class<?> proxyClass = proxyClass(Plain.class);
        assertFalse(ReloadJavaProxyCommand.implementsInterfaces(proxyClass, new Class<?>[]{PlainWithOtherReturnType.class}));
    }

    @Test
    public void proxyWithNarrowerReturnType() {
        Class<?> proxyClass = proxyClass(Plain.class);
        assertFalse(ReloadJavaProxyCommand.implementsInterfaces(proxyClass, new Class<?>[]{PlainWithWiderReturnType.class}));
    }

    @Test
    public void proxyWithOtherExceptions() {
        Class<?> proxyClass = proxyClass(Plain.class);
        assertFalse(ReloadJavaProxyCommand.implementsInterfaces(proxyClass, new Class<?>[]{PlainWithOtherException.class}));
    }

    @Test
    public void proxyWithoutMethodOfOneOfDuplicateMethods() {
        Class<?> proxyClass = proxyClass(ObjectGetter.class, StringGetter.class);
        assertFalse(ReloadJavaProxyCommand.implementsInterfaces(proxyClass,
                new Class<?>[]{CharSequenceGetter.class, StringGetter.class}));
    }

    private static void assertImplements(Class<?>... interfaces) {
        Class<?> proxyClass = proxyClass(interfaces);
        assertTrue(proxyClass.getName(), ReloadJavaProxyCommand.implementsInterfaces(proxyClass, proxyClass.getInterfaces()));
    }

    private static Class<?> proxyClass(Class<?>... interfaces) {
        return Proxy.newProxyInstance(ReloadJavaProxyCommandTest.class.getClassLoader(), interfaces,
                (proxy, method, args) -> null).getClass();
    }
}
