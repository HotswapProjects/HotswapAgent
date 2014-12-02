package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.instrument.IllegalClassFormatException;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeTransformer;
import org.hotswap.agent.plugin.proxy.SinglestepProxyTransformer;

/**
 * Redefines Cglib Enhancer proxy classes. Uses CglibEnhancerProxyBytecodeGenerator for the bytecode generation.
 * 
 * @author Erki Ehtla
 * 
 */
public class CglibEnhancerProxyTransformer extends SinglestepProxyTransformer {
	
	private GeneratorParams params;
	private ClassLoader loader;
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param classPool
	 *            Classpool of the classloader
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @param loader
	 *            classloader of classBeingRedefined
	 * @param params
	 *            parameters used to generate proxy
	 * @throws IllegalClassFormatException
	 */
	public CglibEnhancerProxyTransformer(Class<?> classBeingRedefined, ClassPool classPool, byte[] classfileBuffer,
			ClassLoader loader, GeneratorParams params) {
		super(classBeingRedefined, classPool, classfileBuffer);
		this.loader = loader;
		this.params = params;
	}
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param cc
	 *            CtClass from classfileBuffer
	 * @param cp
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @param loader
	 *            ClassLoader of the classBeingRedefined
	 * @return classfileBuffer or new Proxy defition if there are signature changes
	 * @throws IllegalClassFormatException
	 */
	public static byte[] transform(Class<?> classBeingRedefined, ClassPool classPool, byte[] classfileBuffer,
			ClassLoader loader, GeneratorParams params) throws Exception {
		return new CglibEnhancerProxyTransformer(classBeingRedefined, classPool, classfileBuffer, loader, params)
				.transformRedefine();
	}
	
	@Override
	protected ProxyBytecodeGenerator createGenerator() {
		return new CglibEnhancerProxyBytecodeGenerator(params, loader);
	}
	
	@Override
	protected ProxyBytecodeTransformer createTransformer() {
		return new CglibProxyBytecodeTransformer(classPool);
	}
}
