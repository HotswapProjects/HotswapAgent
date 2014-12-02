package org.hotswap.agent.plugin.spring.test.signature;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.spring.signature.CtClassSignature;
import org.hotswap.agent.plugin.spring.signature.JavaClassSignature;
import org.junit.Test;

public class SignatureTest {
	
	public enum SigTestEnum {
		NEW, FINISHED, WAITING
	}
	
	@Target({ ElementType.PARAMETER, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Asd {
		SigTestEnum value() default SigTestEnum.FINISHED;
		
		SigTestEnum value2() default SigTestEnum.FINISHED;
		
		// TransformationState value3();
	}
	
	@Target({ ElementType.PARAMETER, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Bcd {
	}
	
	@Asd(value2 = SigTestEnum.FINISHED)
	@Bcd
	public static class A {
		static {
		}
		
		{
			
		}
	}
	
	@Asd(value = SigTestEnum.WAITING)
	public static class OneMethod {
		public int get9(@Asd(value = SigTestEnum.NEW) @Bcd Object o) {
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
		
		@Override
		public int get9() {
			return 0;
		}
		
		@Override
		public int get3() {
			
			return 0;
		}
		
		@Override
		public int get2() {
			
			return 0;
		}
		
		@Override
		public int get1() {
			
			return 0;
		}
		
		@Override
		public int getA() {
			
			return 0;
		}
		
		@Override
		public int getB() {
			
			return 0;
		}
		
		@Override
		public int[] getBArray() {
			
			return null;
		}
		
		@Override
		public Object[] getBObjectArray() {
			
			return null;
		}
		
		@Override
		public Object getBObject() {
			
			return null;
		}
		
		@Override
		public int get3(int[] a) {
			
			return 0;
		}
		
		@Override
		public int get2(int[] a, int[] b) {
			
			return 0;
		}
		
		@Override
		public int get1(int[]... b) {
			
			return 0;
		}
		
		@Override
		public int getA(Object o) {
			
			return 0;
		}
		
		@Override
		public int getB(Object... o) {
			
			return 0;
		}
		
		@Override
		public int get3(Object[] a) {
			
			return 0;
		}
		
		@Override
		public int get2(Object[] a, Object[] b) {
			
			return 0;
		}
		
		@Override
		public int getA(Object[] a, Object[]... b) {
			return 0;
		}
		
		@Override
		public int getB(Object[]... o) {
			return 0;
		}
		
		@Override
		public int get8() {
			return 0;
		}
		
		public int get123() throws IOException, NotFoundException {
			return 0;
		}
	}
	
	public interface TestSignatures {
		// test ordering
		public int get3();
		
		public int get2();
		
		public int get1();
		
		public int getA();
		
		public int getB();
		
		// test return types
		
		public int[] getBArray();
		
		public Object[] getBObjectArray();
		
		public Object getBObject();
		
		// test parameters
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
	public void testInterfaceSignature() throws NotFoundException, ClassNotFoundException {
		CtClass makeClass = ClassPool.getDefault().get(TestSignatures.class.getName());
		String expected = JavaClassSignature.get(TestSignatures.class);
		String actual = CtClassSignature.get(makeClass);
		assertEquals("Signatures not equal", expected, actual);
	}
	
	@Test
	public void testClassSignature() throws NotFoundException, ClassNotFoundException {
		{
			CtClass makeClass = ClassPool.getDefault().get(A.class.getName());
			String expected = JavaClassSignature.get(A.class);
			String actual = CtClassSignature.get(makeClass);
			assertEquals("Signatures not equal", expected, actual);
		}
		{
			CtClass makeClass = ClassPool.getDefault().get(C.class.getName());
			String expected = JavaClassSignature.get(C.class);
			String actual = CtClassSignature.get(makeClass);
			assertEquals("Signatures not equal", expected, actual);
		}
	}
	
	@Test
	public void testAbstractClassSignature() throws NotFoundException, ClassNotFoundException {
		CtClass makeClass = ClassPool.getDefault().get(B.class.getName());
		String expected = JavaClassSignature.get(B.class);
		String actual = CtClassSignature.get(makeClass);
		assertEquals("Signatures not equal", expected, actual);
	}
	
	@Test
	public void testOne() throws NotFoundException, ClassNotFoundException {
		CtClass makeClass = ClassPool.getDefault().get(OneMethod.class.getName());
		String expected = JavaClassSignature.get(OneMethod.class);
		String actual = CtClassSignature.get(makeClass);
		assertEquals("Signatures not equal", expected, actual);
	}
}