package org.hotswap.agent.plugin.proxy.java;

import java.lang.instrument.IllegalClassFormatException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureComparer;

/**
 * Proxy transformer for java.lang.reflect.Proxy. One-step process.
 * 
 * @author Erki Ehtla
 * 
 */
public class JavassistProxyTransformer {
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	
	protected Map<Class<?>, Long> transStart = new ConcurrentHashMap<Class<?>, Long>();
	private static AgentLogger LOGGER = AgentLogger.getLogger(JavassistProxyTransformer.class);
	
	// @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transform(final Class<?> classBeingRedefined, final CtClass cc, ClassPool cp,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			if (!isProxy(classBeingRedefined.getName())
					|| !ClassfileSignatureComparer.isPoolClassOrParentDifferent(classBeingRedefined, cp)) {
				return classfileBuffer;
			}
			byte[] generateProxyClass = CtClassJavaProxyGenerator.generateProxyClass(classBeingRedefined.getName(),
					cc.getInterfaces(), cp);
			LOGGER.reload("Class '{}' has been reloaded.", classBeingRedefined.getName());
			return generateProxyClass;
		} catch (Exception e) {
			LOGGER.error("Error transforming a Java reflect Proxy", e);
			return classfileBuffer;
		}
	}
	
	private static boolean isProxy(String className) {
		return className.startsWith("com.sun.proxy.$Proxy");
	}
}