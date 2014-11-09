/**
 * 
 */
package org.hotswap.agent.plugin.proxy;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.plugin.proxy.cglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.cglib.GeneratorParametersRecorder;
import org.hotswap.agent.plugin.proxy.java.JavassistProxyTransformer;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureRecorder;

/**
 * @author Erki Ehtla
 * 
 */
@Plugin(name = "Proxy", description = "Redefines proxies", testedVersions = { "" }, expectedVersions = { "all" }, supportClass = {
		ClassfileSignatureRecorder.class, GeneratorParametersRecorder.class, JavassistProxyTransformer.class,
		CglibProxyTransformer.class })
public class ProxyPlugin {
}
