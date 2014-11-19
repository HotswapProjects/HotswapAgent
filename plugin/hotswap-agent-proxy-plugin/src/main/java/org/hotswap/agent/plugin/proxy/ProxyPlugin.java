package org.hotswap.agent.plugin.proxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.hscglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersTransformer;
import org.hotswap.agent.plugin.proxy.java.JavaProxyTransformer;
import org.hotswap.agent.plugin.proxy.java.JavassistProxyTransformer;

/**
 * Redefines proxy classes that implement or extend changed interfaces or classes. Currently it supports proxies created
 * with Java reflection and the Cglib library.
 * 
 * 
 * @author Erki Ehtla
 * 
 */
@Plugin(name = "Proxy", description = "Redefines proxies", testedVersions = { "" }, expectedVersions = { "all" })
public class ProxyPlugin {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyPlugin.class);
	
	/**
	 * Transforms the proxies after changes
	 * 
	 * We need to have all the changed classes in the ClassPool, so the ClassfileSignatureComparer can determine if a
	 * classhierarchy has been changed and also for the one-step JavassistProxyTransformer to see the changed interfaces
	 */
	@OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transformRedefinitions(final Class<?> classBeingRedefined, final byte[] classfileBuffer,
			final ClassLoader loader) throws IllegalClassFormatException, IOException, RuntimeException {
		// load the class in the pool for ClassfileSignatureComparer and JavassistProxyTransformer
		ClassPool cp = ProxyTransformationUtils.getClassPool(loader);
		CtClass cc = cp.makeClass(new ByteArrayInputStream(classfileBuffer), false);
		
		byte[] result = classfileBuffer;
		
		boolean useJavassistProxyTransformer = true;
		if (useJavassistProxyTransformer) {
			result = JavassistProxyTransformer.transform(classBeingRedefined, cc, cp, result);
		} else {
			result = JavaProxyTransformer.transform(classBeingRedefined, cc, cp, result);
		}
		return CglibProxyTransformer.transform(classBeingRedefined, cc, cp, result, loader);
	}
	
	/**
	 * Modifies Cglib bytecode generators to store the parameters for this plugin
	 */
	@OnClassLoadEvent(classNameRegexp = ".*/cglib/.*", events = LoadEvent.DEFINE, skipSynthetic = false)
	public static byte[] transformDefinitions(final byte[] classfileBuffer, final ClassLoader loader)
			throws IllegalClassFormatException, IOException, RuntimeException {
		CtClass cc = ProxyTransformationUtils.getClassPool(loader).makeClass(new ByteArrayInputStream(classfileBuffer),
				false);
		try {
			return GeneratorParametersTransformer.transform(cc);
		} finally {
			// we dont need to store defined CtClass-es in the pool
			cc.detach();
		}
	}
	
	{
		LOGGER.info("Proxy plugin initialized");
	}
}
