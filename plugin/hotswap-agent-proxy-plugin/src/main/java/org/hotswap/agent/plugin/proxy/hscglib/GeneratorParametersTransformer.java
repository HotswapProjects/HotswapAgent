package org.hotswap.agent.plugin.proxy.hscglib;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.MethodInfo;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Inits plugin and adds byte generation parameter storing
 * 
 * @author Erki Ehtla
 * 
 */
public class GeneratorParametersTransformer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(GeneratorParametersTransformer.class);
	private static Map<ClassLoader, WeakReference<Map<String, GeneratorParams>>> classLoaderMaps = new WeakHashMap<ClassLoader, WeakReference<Map<String, GeneratorParams>>>();
	
	/**
	 * Add plugin init calls and byte generation parameter storing
	 * 
	 * @param cc
	 * @return
	 */
	// @OnClassLoadEvent(classNameRegexp = ".*cglib.*", events = LoadEvent.DEFINE)
	public static byte[] transform(CtClass cc) {
		try {
			if (isGeneratorStrategy(cc)) {
				String initalizer = "{" + PluginManagerInvoker.buildInitializePlugin(ProxyPlugin.class) + "}";
				cc.defrost();
				for (CtConstructor constructor : cc.getDeclaredConstructors()) {
					constructor.insertAfter(initalizer);
				}
				for (CtMethod method : cc.getDeclaredMethods()) {
					if (!Modifier.isAbstract(method.getModifiers()) && method.getName().equals("generate")
							&& method.getMethodInfo().getDescriptor().endsWith("[B")) {
						method.insertAfter("org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersRecorder.register($0, $1, $_);");
					}
				}
				return cc.toBytecode();
			}
		} catch (RuntimeException | CannotCompileException | IOException e) {
			LOGGER.error("error modifying class for cglib proxy creation parameter recording", e);
		}
		return null;
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
	 * Retrieves Class name, GeneratorParams Map from within a classloader
	 * 
	 * @param loader
	 * @return Map of Class names and parameters used for Proxy creation
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, GeneratorParams> getGeneratorParams(ClassLoader loader) {
		try {
			WeakReference<Map<String, GeneratorParams>> mapRef = classLoaderMaps.get(loader);
			if (mapRef == null) {
				synchronized (classLoaderMaps) {
					if (mapRef == null) {
						mapRef = classLoaderMaps.get(loader);
						Map<String, GeneratorParams> map = (Map<String, GeneratorParams>) loader
								.loadClass("org.hotswap.agent.plugin.proxy.hscglib.GeneratorParametersRecorder")
								.getField("generatorParams").get(null);
						mapRef = new WeakReference<Map<String, GeneratorParams>>(map);
						classLoaderMaps.put(loader, mapRef);
						
					}
				}
			}
			Map<String, GeneratorParams> map = mapRef.get();
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
}
