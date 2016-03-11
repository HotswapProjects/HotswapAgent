package org.hotswap.agent.plugin.proxy;

/**
 * 
 * @author Erki Ehtla
 * 
 *         Generates new bytecode for a proxy
 * 
 */
public interface ProxyBytecodeGenerator {
	/**
	 * Generates new bytecode for the proxy
	 * 
	 * @return new bytecode of the changed proxy
	 * @throws Exception
	 */
	public byte[] generate() throws Exception;
}
