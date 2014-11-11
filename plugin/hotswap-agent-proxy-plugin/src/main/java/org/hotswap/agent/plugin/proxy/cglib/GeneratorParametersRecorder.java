package org.hotswap.agent.plugin.proxy.cglib;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyPlugin;
import org.hotswap.agent.plugin.proxy.ProxyTransformationUtils;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * @author Erki Ehtla
 * 
 */
public class GeneratorParametersRecorder {
	
	public static Map<String, GeneratorParams> generatorParams = new ConcurrentHashMap<>();
	private static final ClassPool classPool = ProxyTransformationUtils.getClassPool();
	private static AgentLogger LOGGER = AgentLogger.getLogger(GeneratorParametersRecorder.class);
	
	// @OnClassLoadEvent(classNameRegexp = ".*cglib.*", events = LoadEvent.DEFINE)
	public static byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
		CtClass cc;
		try {
			cc = classPool.makeClass(new ByteArrayInputStream(classfileBuffer), false);
			CtClass[] interfaces = cc.getInterfaces();
			for (CtClass class1 : interfaces) {
				// We use class name strings because some libraries repackage cglib to a different namespace to avoid
				// conflicts.
				if (class1.getSimpleName().equals("GeneratorStrategy")) {
					CtMethod[] declaredMethods = class1.getMethods();
					for (CtMethod method : declaredMethods) {
						if (method.getName().equals("generate")
								&& method.getReturnType().getSimpleName().equals("byte[]")) {
							
							String initalizer = "{"
									+ PluginManagerInvoker.buildInitializePlugin(ProxyPlugin.class)
									+ PluginManagerInvoker.buildCallPluginMethod(ProxyPlugin.class,
											"initEnhancerProxyPlugin") + "}";
							
							for (CtConstructor constructor : cc.getDeclaredConstructors()) {
								constructor.insertAfter(initalizer);
							}
							
							return addGenerationParameterCollector(cc);
						}
					}
				}
			}
		} catch (IOException | RuntimeException | NotFoundException | CannotCompileException e) {
			LOGGER.error("error modifying class for cglib proxy creation parameter recording", e);
		}
		return null;
	}
	
	public static void register(Object generatorStrategy, Object classGenerator, byte[] bytes) {
		CtClass cc = null;
		try {
			cc = classPool.makeClass(new ByteArrayInputStream(bytes), false);
			generatorParams.put(cc.getName(), new GeneratorParams(generatorStrategy, classGenerator));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Error saving parameters of a creation of a Cglib proxy", e);
		}
	}
	
	private static byte[] addGenerationParameterCollector(final CtClass cc) throws IOException, NotFoundException,
			CannotCompileException {
		CtMethod declaredMethod = cc.getDeclaredMethod("generate");
		declaredMethod.insertAfter(GeneratorParametersRecorder.class.getName() + ".register($0, $1, $_);");
		return cc.toBytecode();
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
