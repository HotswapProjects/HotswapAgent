package org.hotswap.agent.plugin.proxy.test.signature;

import static org.junit.Assert.*;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.proxy.signature.CtClassSignature;
import org.hotswap.agent.plugin.proxy.signature.JavaClassSignature;
import org.junit.Test;

public class SignatureTest {
	public static class A {
		static {
		}
		
		{
			
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
		
		public int get2(Object[] a, Object[] b);
		
		public int getA(Object[] a, Object[]... b);
		
		public int getB(Object[]... o);
	}
	
	@Test
	public void testInterfaceSignature() throws NotFoundException {
		CtClass makeClass = ClassPool.getDefault().get(TestSignatures.class.getName());
		String expected = JavaClassSignature.get(TestSignatures.class);
		String actual = CtClassSignature.get(makeClass);
		assertEquals("Signatures not equal", expected, actual);
	}
	
	@Test
	public void testClassSignature() throws NotFoundException {
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
	public void testAbstractClassSignature() throws NotFoundException {
		CtClass makeClass = ClassPool.getDefault().get(B.class.getName());
		String expected = JavaClassSignature.get(B.class);
		String actual = CtClassSignature.get(makeClass);
		assertEquals("Signatures not equal", expected, actual);
	}
}