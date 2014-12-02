package org.hotswap.agent.plugin.proxy;

/**
 * Transforms the bytecode of a new proxy definition so it can be used after redefinition
 * 
 * @author Erki Ehtla
 * 
 */
public interface ProxyBytecodeTransformer {
	public static final String INIT_FIELD_PREFIX = "initCalled";
	
	public byte[] transform(byte[] classfileBuffer) throws Exception;
	
}
