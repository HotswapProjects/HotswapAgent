/**
 * 
 */
package org.hotswap.agent.plugin.proxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.hscglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersRecorder;
import org.hotswap.agent.plugin.proxy.java.JavaProxyTransformer;
import org.hotswap.agent.plugin.proxy.java.JavassistProxyTransformer;

/**
 * @author Erki Ehtla
 * 
 */
@Plugin(name = "Proxy", description = "Redefines proxies", testedVersions = { "" }, expectedVersions = { "all" }
// cannot use supportClass, because the order of transformers is important and the ordering was not reliable with
// supportClass
// , supportClass = { ClassfileSignatureRecorder.class, GeneratorParametersRecorder.class,
// JavassistProxyTransformer.class, CglibProxyTransformer.class }
)
public class ProxyPlugin {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyPlugin.class);
	@Init
	static ClassLoader loader;
	
	@OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transformRedefinitions(String className, final Class<?> classBeingRedefined,
			final byte[] classfileBuffer) throws IllegalClassFormatException, IOException, RuntimeException {
		CtClass cc = ProxyTransformationUtils.getOrCreateClassPool(loader).makeClass(
				new ByteArrayInputStream(classfileBuffer), false);
		// ClassfileSignatureRecorder.record(cc);
		byte[] transform;
		boolean useJavassistProxyTransformer = false;// loader == ProxyPlugin.class.getClassLoader();
		if (useJavassistProxyTransformer)
			transform = JavassistProxyTransformer.transform(className, classBeingRedefined, cc);
		else {
			transform = new JavaProxyTransformer(loader, className, classBeingRedefined, classfileBuffer).transform();
		}
		byte[] result = nvl(transform, classfileBuffer);
		transform = new CglibProxyTransformer(loader, className, classBeingRedefined, result).transform();
		return nvl(transform, result);
	}
	
	private static byte[] nvl(final byte[] result, byte[] transform) {
		return result != null ? result : transform;
	}
	
	@OnClassLoadEvent(classNameRegexp = ".*/cglib/.*", events = LoadEvent.DEFINE, skipSynthetic = false)
	public static byte[] transformDefinitions(final byte[] classfileBuffer) throws IllegalClassFormatException,
			IOException, RuntimeException {
		CtClass cc = ProxyTransformationUtils.getOrCreateClassPool(loader).makeClass(
				new ByteArrayInputStream(classfileBuffer));
		return GeneratorParametersRecorder.transform(cc);
	}
	
	public static void initEnhancerProxyPlugin() {
		LOGGER.info("Proxy plugin initialized");
	}
}
