package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.MethodInfo;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Inits plugin and adds bytecode generation call parameter recording
 * 
 * @author Erki Ehtla
 * 
 */
public class GeneratorParametersTransformer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(GeneratorParametersTransformer.class);
	private static Map<ClassLoader, WeakReference<Map<String, Object>>> classLoaderMaps = new WeakHashMap<ClassLoader, WeakReference<Map<String, Object>>>();
	
	/**
	 * Adds bytecode generation call parameter recording
	 * 
	 * @param cc
	 * @return
	 * @throws Exception
	 */
	public static CtClass transform(CtClass cc) throws Exception {
		if (isGeneratorStrategy(cc)) {
			for (CtMethod method : cc.getDeclaredMethods()) {
				if (!Modifier.isAbstract(method.getModifiers()) && method.getName().equals("generate")
						&& method.getMethodInfo().getDescriptor().endsWith(";)[B")) {
					cc.defrost();
					method.insertAfter("org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersRecorder.register($0, $1, $_);");
				}
			}
		}
		return cc;
	}
	
	/**
	 * Determines if a Class is a Cglib GeneratorStrategy subclass
	 * 
	 * @param cc
	 * @return
	 */
	private static boolean isGeneratorStrategy(CtClass cc) {
		String[] interfaces = cc.getClassFile2().getInterfaces();
		for (String interfaceName : interfaces) {
			// We use class name strings because some libraries repackage cglib to a different namespace to avoid
			// conflicts.
			if (interfaceName.endsWith(".GeneratorStrategy")) {
				@SuppressWarnings("unchecked")
				List<MethodInfo> methodInfos = cc.getClassFile2().getMethods();
				for (MethodInfo method : methodInfos) {
					if (method.getName().equals("generate") && method.getDescriptor().endsWith("[B")) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Retrieves GeneratorParams Map from within a ClassLoader
	 * 
	 * @param loader
	 * @return Map of Class names and parameters used for Proxy creation
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> getGeneratorParamsMap(ClassLoader loader) {
		try {
			WeakReference<Map<String, Object>> mapRef;
			synchronized (classLoaderMaps) {
				mapRef = classLoaderMaps.get(loader);
				if (mapRef == null) {
					mapRef = classLoaderMaps.get(loader);
					Map<String, Object> map = (Map<String, Object>) loader
							.loadClass(GeneratorParametersRecorder.class.getName()).getField("generatorParams")
							.get(null);
					mapRef = new WeakReference<Map<String, Object>>(map);
					classLoaderMaps.put(loader, mapRef);
				}
			}
			Map<String, Object> map = mapRef.get();
			if (map == null) {
				return new HashMap<>();
			}
			return map;
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| ClassNotFoundException e) {
			LOGGER.error("Unable to access field with proxy generation parameters. Proxy redefinition failed.");
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Retrieves GeneratorParams from within a ClassLoader
	 * 
	 * @param loader
	 * @param name
	 *            Class name
	 * @return GeneratorParams instance in this ClassLoader
	 */
	public static GeneratorParams getGeneratorParams(ClassLoader loader, String name) {
		Object generatorParams = getGeneratorParamsMap(loader).get(name);
		if (generatorParams != null) {
			try {
				return GeneratorParams.valueOf(generatorParams);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}
}
