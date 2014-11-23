package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.plugin.proxy.AbstractProxyTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

/**
 * AbstractProxyTransformer based Proxy transformer for Cglib proxies. Takes one redefinition event to transform a
 * Classc reated with an Enhancer and 2 events for everything else.
 * 
 * @author Erki Ehtla
 * 
 */
public class CglibProxyTransformer extends AbstractProxyTransformer {
	// Class transformation states for all the ClassLoaders. Used in the Agent ClassLoader
	private static final Map<Class<?>, TransformationState> TRANSFORMATION_STATES = Collections
			.synchronizedMap(new WeakHashMap<Class<?>, TransformationState>());
	
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
	public CglibProxyTransformer(Class<?> classBeingRedefined, byte[] classfileBuffer, ClassLoader loader) {
		super(classBeingRedefined, classfileBuffer, loader, TRANSFORMATION_STATES);
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
	public static byte[] transform(Class<?> classBeingRedefined, byte[] classfileBuffer, ClassLoader loader)
			throws IllegalClassFormatException {
		return new CglibProxyTransformer(classBeingRedefined, classfileBuffer, loader).transform();
	}
	
	@Override
	protected boolean isProxy() {
		return isProxy(loader, javaClassName);
	}
	
	public static boolean isProxy(ClassLoader loader, String javaClassName) {
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
		return strB.toString() + "CGLIB$BIND_CALLBACKS(this);";
	}
	
	@Override
	protected byte[] getNewByteCode() throws Exception {
		GeneratorParams param = GeneratorParametersTransformer.getGeneratorParams(loader).get(javaClassName);
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		if (isGeneratedByEnhancer(param)) {
			return new EnhancerCreater(param, getNewClassLoader()).create();
		}
		
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
	
	@Override
	protected byte[] handleNewClass() throws Exception {
		if (isGeneratedByEnhancer()) {
			return generateNewProxyClass();
		} else {
			return super.handleNewClass();
			
		}
	}
	
	private boolean isGeneratedByEnhancer() {
		GeneratorParams generatorParams = GeneratorParametersTransformer.getGeneratorParams(loader).get(javaClassName);
		if (generatorParams == null)
			return false;
		return isGeneratedByEnhancer(generatorParams);
	}
	
	private boolean isGeneratedByEnhancer(GeneratorParams generatorParams) {
		return generatorParams.getParam().getClass().getName().endsWith(".Enhancer");
	}
}
