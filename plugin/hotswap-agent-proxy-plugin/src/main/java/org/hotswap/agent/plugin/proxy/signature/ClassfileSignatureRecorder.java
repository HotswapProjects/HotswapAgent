package org.hotswap.agent.plugin.proxy.signature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyTransformationUtils;

/**
 * @author Erki Ehtla
 * 
 */
public class ClassfileSignatureRecorder {
	
	private static Map<String, String> classSignatures = new ConcurrentHashMap<>();
	protected static final ClassPool classPool = ProxyTransformationUtils.getClassPool();
	private static AgentLogger LOGGER = AgentLogger.getLogger(ClassfileSignatureRecorder.class);
	
	@OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
		try {
			CtClass cc = classPool.makeClass(new ByteArrayInputStream(classfileBuffer), false);
			classSignatures.put(classBeingRedefined.getName(), CtClassSignature.get(cc));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Error saving class signature of a changed class", e);
		}
		return null;
	}
	
	public static boolean hasClassChanged(Class<?> clazz) {
		String ctClassString = classSignatures.get(clazz.getName());
		if (ctClassString == null)
			return false;
		return !JavaClassSignature.get(clazz).equals(ctClassString);
	}
	
	public static boolean hasSuperClassOrInterfaceChanged(Class<?> clazz) {
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			if (ClassfileSignatureRecorder.hasClassChanged(superclass) || superclass.getSuperclass() != null
					&& hasSuperClassOrInterfaceChanged(superclass)) {
				return true;
			}
		}
		Class<?>[] interfaces = clazz.getInterfaces();
		for (Class<?> interfaceClazz : interfaces) {
			if (ClassfileSignatureRecorder.hasClassChanged(interfaceClazz) || interfaceClazz.getSuperclass() != null
					&& hasSuperClassOrInterfaceChanged(interfaceClazz)) {
				return true;
			}
		}
		return false;
	}
}
