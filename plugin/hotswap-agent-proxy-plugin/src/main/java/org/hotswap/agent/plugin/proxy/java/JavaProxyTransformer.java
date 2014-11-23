package org.hotswap.agent.plugin.proxy.java;

import java.lang.instrument.IllegalClassFormatException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.plugin.proxy.AbstractProxyTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

import sun.misc.ProxyGenerator;

/**
 * AbstractProxyTransformer based Proxy transformer for java.lang.reflect.Proxy proxies. Takes more than one
 * redefinition event to transform a Class.
 * 
 * @author Erki Ehtla
 * 
 */
public class JavaProxyTransformer extends AbstractProxyTransformer {
	
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
	 * @throws IllegalClassFormatException
	 */
	public JavaProxyTransformer(Class<?> classBeingRedefined, ClassLoader loader, byte[] classfileBuffer) {
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
	 * @return classfileBuffer or new Proxy defition if there are signature changes
	 * @throws IllegalClassFormatException
	 */
	public static byte[] transform(Class<?> classBeingRedefined, ClassLoader loader, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		return new JavaProxyTransformer(classBeingRedefined, loader, classfileBuffer).transform();
	}
	
	@Override
	protected String getInitCall(CtClass cc, String random) throws Exception {
		// clinit method already contains the setting of our static clinitFieldName to true
		CtMethod method = cc.getClassInitializer().toMethod("initMethod" + random, cc);
		method.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		cc.addMethod(method);
		return method.getName() + "();";
	}
	
	@Override
	protected boolean isProxy() {
		return javaClassName.startsWith("com.sun.proxy.$Proxy");
	}
	
	@Override
	protected byte[] getNewByteCode() {
		return ProxyGenerator.generateProxyClass(javaClassName, classBeingRedefined.getInterfaces());
	}
	
	public static boolean isReloadingInProgress() {
		return TRANSFORMATION_STATES.size() > 0;
	}
}
