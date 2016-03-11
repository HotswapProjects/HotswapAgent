package org.hotswap.agent.plugin.proxy.java;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.plugin.proxy.AbstractProxyBytecodeTransformer;

/**
 * Transforms the bytecode of a new Java proxy definition so it is initialized on the first access of one of its
 * methods.
 * 
 * @author Erki Ehtla
 * 
 */
public class JavaProxyBytecodeTransformer extends AbstractProxyBytecodeTransformer {
	public JavaProxyBytecodeTransformer(ClassPool classPool) {
		super(classPool);
	}
	
	@Override
	protected String getInitCall(CtClass cc, String initFieldName) throws Exception {
		// clinit method already contains the setting of our static clinitFieldName to true
		CtMethod method = cc.getClassInitializer().toMethod(initFieldName, cc);
		method.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		cc.addMethod(method);
		return method.getName() + "();";
	}
}
