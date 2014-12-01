package org.hotswap.agent.plugin.proxy.java;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.plugin.proxy.MultistepProxyTransformer;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

/**
 * Redefines Java proxy classes. Uses several redefinition events
 * 
 * @author Erki Ehtla
 * 
 */
public class JavaProxyTransformer extends MultistepProxyTransformer {
	// Class transformation states for all the ClassLoaders. Used in the Agent ClassLoader
	private static final Map<Class<?>, TransformationState> TRANSFORMATION_STATES = Collections
			.synchronizedMap(new WeakHashMap<Class<?>, TransformationState>());
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param cp
	 *            Classpool of the classloader
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 */
	public JavaProxyTransformer(Class<?> classBeingRedefined, ClassPool cp, byte[] classfileBuffer) {
		super(classBeingRedefined, cp, classfileBuffer, TRANSFORMATION_STATES);
	}
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param cp
	 *            Classpool of the classloader
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @return classfileBuffer or new Proxy defition if there are signature changes
	 * @throws Exception
	 */
	public static byte[] transform(Class<?> classBeingRedefined, ClassPool cp, byte[] classfileBuffer) throws Exception {
		return new JavaProxyTransformer(classBeingRedefined, cp, classfileBuffer).transformRedefine();
	}
	
	public static boolean isReloadingInProgress() {
		return !TRANSFORMATION_STATES.isEmpty();
	}
	
	@Override
	protected ProxyBytecodeGenerator createGenerator() {
		return new JavaProxyBytecodeGenerator(classBeingRedefined);
	}
	
	@Override
	protected ProxyBytecodeTransformer createTransformer() {
		return new JavaProxyBytecodeTransformer(classPool);
	}
}
