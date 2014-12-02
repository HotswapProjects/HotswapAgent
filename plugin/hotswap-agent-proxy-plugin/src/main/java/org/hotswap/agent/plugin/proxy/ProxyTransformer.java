package org.hotswap.agent.plugin.proxy;

/**
 * Redefines a proxy
 * 
 * @author Erki Ehtla
 * 
 */
public interface ProxyTransformer {
	
	public byte[] transformRedefine() throws Exception;
	
}
