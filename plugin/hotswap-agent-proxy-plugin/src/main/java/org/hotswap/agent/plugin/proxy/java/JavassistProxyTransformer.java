package org.hotswap.agent.plugin.proxy.java;

import java.lang.instrument.IllegalClassFormatException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureRecorder;

/**
 * @author Erki Ehtla
 * 
 */
public class JavassistProxyTransformer {
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	
	protected Map<Class<?>, Long> transStart = new ConcurrentHashMap<Class<?>, Long>();
	private static AgentLogger LOGGER = AgentLogger.getLogger(JavassistProxyTransformer.class);
	
	// @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transform(String className, final Class<?> classBeingRedefined, final CtClass cc)
			throws IllegalClassFormatException {
		try {
			if (!isProxy(className) || !ClassfileSignatureRecorder.hasSuperClassOrInterfaceChanged(classBeingRedefined)) {
				return null;
			}
			String javaClassName = Descriptor.toJavaName(className);
			byte[] generateProxyClass = CtClassJavaProxyGenerator.generateProxyClass(javaClassName, cc.getInterfaces());
			LOGGER.reload("Class '{}' has been reloaded.", javaClassName);
			return generateProxyClass;
		} catch (Exception e) {
			LOGGER.error("Error transforming a Java reflect Proxy", e);
			return null;
		}
	}
	
	private static boolean isProxy(String className) {
		return className.startsWith("com/sun/proxy/$Proxy");
	}
}