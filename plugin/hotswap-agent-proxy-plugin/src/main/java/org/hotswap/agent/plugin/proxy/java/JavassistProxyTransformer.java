package org.hotswap.agent.plugin.proxy.java;

import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyTransformationUtils;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureRecorder;

/**
 * @author Erki Ehtla
 * 
 */
public class JavassistProxyTransformer {
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	protected static final ClassPool classPool = ProxyTransformationUtils.getClassPool();
	
	protected Map<Class<?>, Long> transStart = new ConcurrentHashMap<Class<?>, Long>();
	private static AgentLogger LOGGER = AgentLogger.getLogger(JavassistProxyTransformer.class);
	
	// @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			if (!isProxy(className, classBeingRedefined, classfileBuffer)
					|| !ClassfileSignatureRecorder.hasSuperClassOrInterfaceChanged(classBeingRedefined)) {
				return null;
			}
			String javaClassName = ProxyTransformationUtils.getClassName(className);
			CtClass cc = classPool.get(javaClassName);
			byte[] generateProxyClass = CtClassJavaProxyGenerator.generateProxyClass(javaClassName, cc.getInterfaces());
			LOGGER.reload("Class '{}' has been reloaded.", javaClassName);
			return generateProxyClass;
		} catch (Exception e) {
			LOGGER.error("Error transforming a Java reflect Proxy", e);
			return null;
		}
	}
	
	private static boolean isProxy(String className, Class<?> classBeingRedefined, byte[] classfileBuffer) {
		return className.startsWith("com/sun/proxy/$Proxy");
	}
}