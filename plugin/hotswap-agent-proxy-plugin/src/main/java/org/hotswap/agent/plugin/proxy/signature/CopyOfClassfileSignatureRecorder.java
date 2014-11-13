package org.hotswap.agent.plugin.proxy.signature;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;

/**
 * @author Erki Ehtla
 * 
 */
public class CopyOfClassfileSignatureRecorder {
	
	private static Map<String, String> classSignatures = new ConcurrentHashMap<>();
	private static AgentLogger LOGGER = AgentLogger.getLogger(CopyOfClassfileSignatureRecorder.class);
	
	// @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] record(CtClass cc) {
		try {
			classSignatures.put(cc.getName(), CtClassSignature.get(cc));
		} catch (RuntimeException e) {
			LOGGER.error("Error saving class signature of a changed class", e);
		}
		return null;
	}
	
	private static boolean hasClassChanged(Class<?> clazz) {
		String ctClassString = classSignatures.get(clazz.getName());
		if (ctClassString == null)
			return false;
		return !JavaClassSignature.get(clazz).equals(ctClassString);
	}
	
	public static boolean hasSuperClassOrInterfaceChanged(Class<?> clazz) {
		if (hasClassChanged(clazz))
			return true;
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			if (CopyOfClassfileSignatureRecorder.hasSuperClassOrInterfaceChanged(superclass)) {
				return true;
			}
		}
		Class<?>[] interfaces = clazz.getInterfaces();
		for (Class<?> interfaceClazz : interfaces) {
			if (CopyOfClassfileSignatureRecorder.hasSuperClassOrInterfaceChanged(interfaceClazz)) {
				return true;
			}
		}
		return false;
	}
}
