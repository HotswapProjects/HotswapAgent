package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.plugin.proxy.AbstractProxyTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

/**
 * @author Erki Ehtla
 * 
 */
public class CglibProxyTransformer extends AbstractProxyTransformer {
	private static final ConcurrentHashMap<Class<?>, TransformationState> TRANSFORMATION_STATES = new ConcurrentHashMap<Class<?>, TransformationState>();
	
	public CglibProxyTransformer(ClassLoader loader, String className, Class<?> classBeingRedefined,
			byte[] classfileBuffer) {
		super(loader, className, classBeingRedefined, classfileBuffer, TRANSFORMATION_STATES);
	}
	
	@Override
	protected boolean isProxy() {
		return GeneratorParametersRecorder.getGeneratorParams(loader).containsKey(javaClassName);
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
		GeneratorParams param = GeneratorParametersRecorder.getGeneratorParams(loader).get(javaClassName);
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		
		return (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
	}
	
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
		return TRANSFORMATION_STATES.size() > 0;
	}
}
