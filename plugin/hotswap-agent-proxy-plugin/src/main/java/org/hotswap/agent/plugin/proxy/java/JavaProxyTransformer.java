package org.hotswap.agent.plugin.proxy.java;

import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.plugin.proxy.AbstractProxyTransformer;
import org.hotswap.agent.plugin.proxy.TransformationState;

import sun.misc.ProxyGenerator;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaProxyTransformer extends AbstractProxyTransformer {
	
	private static final ConcurrentHashMap<Class<?>, TransformationState> TRANSFORMATION_STATES = new ConcurrentHashMap<Class<?>, TransformationState>();
	
	public JavaProxyTransformer(ClassLoader loader, String className, Class<?> classBeingRedefined,
			byte[] classfileBuffer) {
		super(loader, className, classBeingRedefined, classfileBuffer, TRANSFORMATION_STATES);
	}
	
	@Override
	protected String getInitCall(CtClass cc, String random) throws Exception {
		// clinit method already contains the setting of our static clinitFieldName to true
		CtMethod method = cc.getClassInitializer().toMethod("initMethod" + random, cc);
		method.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		cc.addMethod(method);
		return method.getName() + "();";
	}
	
	protected boolean isProxy() {
		return className.startsWith("com/sun/proxy/$Proxy");
	}
	
	@Override
	protected byte[] getNewByteCode() {
		return ProxyGenerator.generateProxyClass(className, classBeingRedefined.getInterfaces());
	}
	
	public static boolean isReloadingInProgress() {
		return TRANSFORMATION_STATES.size() > 0;
	}
}
