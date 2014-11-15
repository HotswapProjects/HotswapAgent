package org.hotswap.agent.plugin.proxy.signature;

import static org.junit.Assert.*;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.junit.Test;

public class SignatureTest {
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
	public void testSignature() throws NotFoundException {
		CtClass makeClass = ClassPool.getDefault().get(TestSignatures.class.getName());
		String expected = JavaClassSignature.get(TestSignatures.class);
		String actual = CtClassSignature.get(makeClass);
		assertEquals("Signatures not equal", expected, actual);
	}
}