/*
 * Copyright 2013-2025 the HotswapAgent authors.
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
package org.hotswap.agent.util.signature;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.junit.Test;

import static org.junit.Assert.*;

public class SignatureTest {

    private static ClassSignatureElement SIGNATURE_ELEMENTS[] = {
            ClassSignatureElement.SUPER_CLASS,
            ClassSignatureElement.INTERFACES,
            ClassSignatureElement.CLASS_ANNOTATION,
            ClassSignatureElement.CONSTRUCTOR,
            ClassSignatureElement.CONSTRUCTOR_PRIVATE,
            ClassSignatureElement.METHOD,
            ClassSignatureElement.METHOD_PRIVATE,
            ClassSignatureElement.METHOD_STATIC,
            ClassSignatureElement.METHOD_ANNOTATION,
            ClassSignatureElement.METHOD_PARAM_ANNOTATION,
            ClassSignatureElement.METHOD_EXCEPTION,
            ClassSignatureElement.FIELD,
            ClassSignatureElement.FIELD_STATIC,
            ClassSignatureElement.FIELD_ANNOTATION
    };

    private static ClassSignatureElement SIGNATURE_ELEMENTS_WITH_SUPERS[] = {
            ClassSignatureElement.SUPER_CLASS,
            ClassSignatureElement.INTERFACES,
            ClassSignatureElement.CLASS_ANNOTATION,
            ClassSignatureElement.CONSTRUCTOR,
            ClassSignatureElement.CONSTRUCTOR_PRIVATE,
            ClassSignatureElement.METHOD,
            ClassSignatureElement.METHOD_PRIVATE,
            ClassSignatureElement.METHOD_STATIC,
            ClassSignatureElement.METHOD_ANNOTATION,
            ClassSignatureElement.METHOD_PARAM_ANNOTATION,
            ClassSignatureElement.METHOD_EXCEPTION,
            ClassSignatureElement.FIELD,
            ClassSignatureElement.FIELD_STATIC,
            ClassSignatureElement.FIELD_ANNOTATION,
            ClassSignatureElement.SUPER_SIGNATURES
    };

    public enum SigTestEnum {
        NEW, FINISHED, WAITING
    }

    @Target({ ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Asd {
        SigTestEnum value() default SigTestEnum.FINISHED;
        SigTestEnum value2() default SigTestEnum.FINISHED;
        String[] array() default {"string"};
        // TransformationState value3();
    }

    @Target({ ElementType.PARAMETER, ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Bcd { }

    @Asd(value2 = SigTestEnum.FINISHED)
    @Bcd
    public static class A {
        static { }
        { }
    }

    @Asd(value = SigTestEnum.WAITING)
    public static class OneMethod {
        @Asd(value = SigTestEnum.WAITING)
        int aField;

        @Asd(value = SigTestEnum.WAITING)
        public int get9(@Asd(value = SigTestEnum.NEW) @Bcd Object o, @Asd(value = SigTestEnum.NEW) @Bcd Object o2) throws IOException {
            return 0;
        }
    }

    public static abstract class B implements TestSignatures {
        public abstract int get8();

        public int get9() {
            return 0;
        }
    }

    public static class C extends B {

        @Override public int get9() { return 0; }
        @Override public int get3() { return 0; }
        @Override public int get2() { return 0; }
        @Override public int get1() { return 0; }
        @Override public int getA() { return 0; }
        @Override public int getB() { return 0; }
        @Override public int[] getBArray() { return null; }
        @Override public Object[] getBObjectArray() { return null; }
        @Override public Object getBObject() { return null; }
        @Override public int get3(int[] a) { return 0; }
        @Override public int get2(int[] a, int[] b) { return 0; }
        @Override public int get1(int[]... b) { return 0; }
        @Override public int getA(Object o) { return 0; }
        @Override public int getB(Object... o) { return 0; }
        @Override public int get3(Object[] a) { return 0; }
        @Override public int get2(Object[] a, Object[] b) { return 0; }
        @Override public int getA(Object[] a, Object[]... b) { return 0; }
        @Override public int getB(Object[]... o) { return 0; }
        @Override public int get8() { return 0; }

        public int get123() throws IOException, NotFoundException {
            return 0;
        }
    }

    public interface TestSignatures {
        public int get3();
        public int get2();
        public int get1();
        public int getA();
        public int getB();

        public int[] getBArray();
        public Object[] getBObjectArray();
        public Object getBObject();

        public int get3(int[] a);
        public int get2(int[] a, int[] b);
        public int get1(int[]... b);

        public int getA(Object o);
        public int getB(Object... o);

        public int get3(Object[] a);
        public int get2(@Asd Object[] a, @Asd Object[] b);

        public int getA(Object[] a, Object[]... b);
        public int getB(Object[]... o);
    }

    @Test
    public void testInterfaceSignature() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(TestSignatures.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(TestSignatures.class, SIGNATURE_ELEMENTS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS);
        assertEquals("Signatures not equal", expected, actual);
    }

    @Test
    public void testClassSignature() throws Exception {
        {
            CtClass makeClass = ClassPool.getDefault().get(A.class.getName());
            String expected = ClassSignatureComparerHelper.getJavaClassSignature(A.class, SIGNATURE_ELEMENTS);
            String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS);
            assertEquals("Signatures not equal", expected, actual);
        }
        {
            CtClass makeClass = ClassPool.getDefault().get(C.class.getName());
            String expected = ClassSignatureComparerHelper.getJavaClassSignature(C.class, SIGNATURE_ELEMENTS);
            String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS);
            assertEquals("Signatures not equal", expected, actual);
        }
    }

    @Test
    public void testAbstractClassSignature() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(B.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(B.class, SIGNATURE_ELEMENTS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS);
        assertEquals("Signatures not equal", expected, actual);
    }

    @Test
    public void testOne() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(OneMethod.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(OneMethod.class, SIGNATURE_ELEMENTS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS);
        assertEquals("Signatures not equal", expected, actual);
    }

    @Test
    public void switchSignatureTest() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(SwitchTestClass.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(SwitchTestClass.class, SIGNATURE_ELEMENTS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS);
        assertEquals("Signatures not equal", expected, actual);
    }

    // -------------------------
    // NEW TESTS FOR SUPER_SIGNATURES
    // -------------------------

    @Test
    public void testClassSignatureWithSupers_JavaVsCt_A() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(A.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(A.class, SIGNATURE_ELEMENTS_WITH_SUPERS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS_WITH_SUPERS);
        assertEquals("Signatures with supers not equal", expected, actual);

        // basic format check
        assertTrue("Signature must start with LOCAL{", expected.startsWith("LOCAL{"));
        assertTrue("Signature must contain }|SUPERS{", expected.contains("}|SUPERS{"));
        assertTrue("Signature must end with }", expected.endsWith("}"));
    }

    @Test
    public void testClassSignatureWithSupers_JavaVsCt_C() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(C.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(C.class, SIGNATURE_ELEMENTS_WITH_SUPERS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS_WITH_SUPERS);
        assertEquals("Signatures with supers not equal", expected, actual);

        assertTrue("Expected to contain superclass B in SUPERS chain", expected.contains(B.class.getName() + "{"));
        assertFalse("SUPERS chain must not contain java.lang.Object", expected.contains(Object.class.getName() + "{"));
    }

    @Test
    public void testAbstractClassSignatureWithSupers_JavaVsCt_B() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(B.class.getName());
        String expected = ClassSignatureComparerHelper.getJavaClassSignature(B.class, SIGNATURE_ELEMENTS_WITH_SUPERS);
        String actual = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS_WITH_SUPERS);
        assertEquals("Abstract class signatures with supers not equal", expected, actual);

        // B directly extends Object => SUPERS should be empty block.
        assertTrue("Expected empty SUPERS block for class directly extending Object", expected.endsWith("|SUPERS{}"));
    }

    @Test
    public void testSignatureWithSupers_Deterministic() throws Exception {
        CtClass makeClass = ClassPool.getDefault().get(C.class.getName());

        String java1 = ClassSignatureComparerHelper.getJavaClassSignature(C.class, SIGNATURE_ELEMENTS_WITH_SUPERS);
        String java2 = ClassSignatureComparerHelper.getJavaClassSignature(C.class, SIGNATURE_ELEMENTS_WITH_SUPERS);
        assertEquals("Java signature with supers must be deterministic", java1, java2);

        String ct1 = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS_WITH_SUPERS);
        String ct2 = ClassSignatureComparerHelper.getCtClassSignature(makeClass, SIGNATURE_ELEMENTS_WITH_SUPERS);
        assertEquals("Ct signature with supers must be deterministic", ct1, ct2);
    }

    @Test
    public void testSignatureWithSupers_ContainsLocalAndSupersParts() throws Exception {
        String sig = ClassSignatureComparerHelper.getJavaClassSignature(C.class, SIGNATURE_ELEMENTS_WITH_SUPERS);

        int localStart = sig.indexOf("LOCAL{");
        int split = sig.indexOf("}|SUPERS{");
        int supersStart = sig.indexOf("|SUPERS{");

        assertTrue("LOCAL part must be present", localStart >= 0);
        assertTrue("LOCAL and SUPERS must be separated by }|SUPERS{", split >= 0);
        assertTrue("SUPERS part must be present", supersStart >= 0);

        String localBody = sig.substring("LOCAL{".length(), split);
        assertTrue("LOCAL body should not be empty", localBody.length() > 0);
    }
}
