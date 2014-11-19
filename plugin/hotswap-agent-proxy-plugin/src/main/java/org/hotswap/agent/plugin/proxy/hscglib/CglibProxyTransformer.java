package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.plugin.proxy.AbstractProxyTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

/**
 * AbstractProxyTransformer based Proxy transformer for Cglib proxies. Takes more than one redefinition event to
 * transform a Class
 * 
 * @author Erki Ehtla
 * 
 */
public class CglibProxyTransformer extends AbstractProxyTransformer {
	// Class transformation states for all the ClassLoaders. Used in the Agent ClassLoader
	private static final ConcurrentHashMap<Class<?>, TransformationState> TRANSFORMATION_STATES = new ConcurrentHashMap<Class<?>, TransformationState>();
	private ClassLoader loader;
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param cc
	 *            CtClass from classfileBuffer
	 * @param cp
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @param loader
	 *            ClassLoader of the classBeingRedefined
	 * @return classfileBuffer or new Proxy defition if there are signature changes
	 * @throws IllegalClassFormatException
	 */
	public CglibProxyTransformer(Class<?> classBeingRedefined, CtClass cc, ClassPool cp, byte[] classfileBuffer,
			ClassLoader loader) {
		super(classBeingRedefined, cc, cp, classfileBuffer, TRANSFORMATION_STATES);
		this.loader = loader;
	}
	
	/**
	 * 
	 * @param classBeingRedefined
	 * @param cc
	 *            CtClass from classfileBuffer
	 * @param cp
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @param loader
	 *            ClassLoader of the classBeingRedefined
	 * @return classfileBuffer or new Proxy defition if there are signature changes
	 * @throws IllegalClassFormatException
	 */
	public static byte[] transform(Class<?> classBeingRedefined, CtClass cc, ClassPool cp, byte[] classfileBuffer,
			ClassLoader loader) throws IllegalClassFormatException {
		return new CglibProxyTransformer(classBeingRedefined, cc, cp, classfileBuffer, loader).transform();
	}
	
	@Override
	protected boolean isProxy() {
		return GeneratorParametersTransformer.getGeneratorParams(loader).containsKey(javaClassName);
	}
	
	@Override
	protected String getInitCall(CtClass cc, String random) throws Exception {
		CtMethod[] methods = cc.getDeclaredMethods();
		StringBuilder strB = new StringBuilder();
		for (CtMethod ctMethod : methods) {
			if (ctMethod.getName().startsWith("CGLIB$STATICHOOK")) {
				ctMethod.insertAfter(INIT_FIELD_PREFIX + random + "=true;");
				strB.insert(0, ctMethod.getName() + "();");
				break;
			}
		}
		
		if (strB.length() == 0)
			throw new RuntimeException("Could not find CGLIB$STATICHOOK method");
		return strB.toString() + ";CGLIB$BIND_CALLBACKS(this);";
	}
	
	@Override
	protected byte[] getNewByteCode() throws Exception {
		GeneratorParams param = GeneratorParametersTransformer.getGeneratorParams(loader).get(javaClassName);
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		
		return (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
	}
	
	/**
	 * Retrieves the actual Method that generates and returns the bytecode
	 * 
	 * @param generator
	 *            GeneratorStrategy instance
	 * @return Method that generates and returns the bytecode
	 */
	private Method getGenerateMethod(Object generator) {
		Method[] methods = generator.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
				return method;
			}
		}
		return null;
	}
	
	public static boolean isReloadingInProgress() {
		return !TRANSFORMATION_STATES.isEmpty();
	}
}
