package org.hotswap.agent.plugin.proxy.hscglib;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.MethodInfo;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyPlugin;
import org.hotswap.agent.plugin.proxy.ProxyTransformationUtils;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * @author Erki Ehtla
 * 
 */
public class GeneratorParametersRecorder {
	// this Map is used in the App ClassLoader
	public static Map<String, GeneratorParams> generatorParams = new ConcurrentHashMap<>();
	private static AgentLogger LOGGER = AgentLogger.getLogger(GeneratorParametersRecorder.class);
	
	/**
	 * Add init plugin calls and add bytegeneration parameter storing
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
						method.insertAfter(GeneratorParametersRecorder.class.getName() + ".register($0, $1, $_);");
					}
				}
				return cc.toBytecode();
			}
		} catch (RuntimeException | CannotCompileException | IOException e) {
			LOGGER.error("error modifying class for cglib proxy creation parameter recording", e);
		}
		return null;
	}
	
	private static boolean isGeneratorStrategy(CtClass cc) {
		String[] interfaces = cc.getClassFile2().getInterfaces();
		for (String interfaceName : interfaces) {
			// We use class name strings because some libraries repackage cglib to a different namespace to avoid
			// conflicts.
			if (interfaceName.endsWith(".GeneratorStrategy")) {
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
	
	public static void register(Object generatorStrategy, Object classGenerator, byte[] bytes) {
		try {
			ClassPool classPool = ProxyTransformationUtils.getClassPool(GeneratorParametersRecorder.class
					.getClassLoader());
			CtClass cc = classPool.makeClass(new ByteArrayInputStream(bytes), false);
			String name = cc.getName();
			cc.detach();
			generatorParams.put(name, new GeneratorParams(generatorStrategy, classGenerator));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Error saving parameters of a creation of a Cglib proxy", e);
		}
	}
	
	public static Map<String, GeneratorParams> getGeneratorParams(ClassLoader loader) {
		try {
			return (Map<String, GeneratorParams>) loader.loadClass(GeneratorParametersRecorder.class.getName())
					.getField("generatorParams").get(null);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| ClassNotFoundException e) {
			LOGGER.error("Unable to access field with proxy generation parameters. Proxy redefinition failed.");
			throw new RuntimeException(e);
		}
	}
}
