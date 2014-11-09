/**
 * 
 */
package org.hotswap.agent.plugin.proxy;

import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.plugin.proxy.cglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.cglib.GeneratorParametersRecorder;
import org.hotswap.agent.plugin.proxy.java.JavassistProxyTransformer;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureRecorder;

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
	
	@OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transformRedefinitions(ClassLoader loader, String className,
			final Class<?> classBeingRedefined, ProtectionDomain protectionDomain, final byte[] classfileBuffer)
			throws IllegalClassFormatException {
		ClassfileSignatureRecorder.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
		byte[] transform = JavassistProxyTransformer.transform(loader, className, classBeingRedefined,
				protectionDomain, classfileBuffer);
		byte[] result = nvl(transform, classfileBuffer);
		transform = CglibProxyTransformer.transform(loader, className, classBeingRedefined, protectionDomain, result);
		return nvl(transform, result);
	}
	
	private static byte[] nvl(final byte[] result, byte[] transform) {
		return result != null ? result : transform;
	}
	
	@OnClassLoadEvent(classNameRegexp = ".*cglib.*", events = LoadEvent.DEFINE, skipSynthetic = false)
	public static byte[] transformDefinitions(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		return GeneratorParametersRecorder.transform(loader, className, classBeingRedefined, protectionDomain,
				classfileBuffer);
	}
}
