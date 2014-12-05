package org.hotswap.agent.plugin.proxy.java;

import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;

import sun.misc.ProxyGenerator;

/**
 * Creates a new bytecode for a Java proxy. Changed Classes have to be already loaded in the App classloader.
 * 
 * @author Erki Ehtla
 * 
 */
public class JavaProxyBytecodeGenerator implements ProxyBytecodeGenerator {
	private Class<?> classBeingRedefined;
	
	public JavaProxyBytecodeGenerator(Class<?> classBeingRedefined) {
		super();
		this.classBeingRedefined = classBeingRedefined;
	}
	
	public byte[] generate() throws Exception {
		return ProxyGenerator.generateProxyClass(classBeingRedefined.getName(), classBeingRedefined.getInterfaces());
	}
}
