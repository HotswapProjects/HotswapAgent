package org.hotswap.agent.plugin.spring.getbean;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;

/**
 * Transforms Spring classes so the beans go through this plugin. The returned beans are proxied and tracked. The bean
 * proxies can be reset and reloaded from Spring.
 * 
 * @author Erki Ehtla
 * 
 */
public class ProxyReplacerTransformer {
	public static final String FACTORY_METHOD_NAME = "getBean";
	
	/**
	 * 
	 * @param ctClass
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory")
	public static void replaceBeanWithProxy(CtClass ctClass) throws NotFoundException, CannotCompileException {
		CtMethod[] methods = ctClass.getMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.getName().equals(FACTORY_METHOD_NAME))
				continue;
			StringBuilder methodParamTypes = new StringBuilder();
			for (CtClass type : ctMethod.getParameterTypes()) {
				methodParamTypes.append(type.getName()).append(".class").append(", ");
			}
			ctMethod.insertAfter("if(true){return org.hotswap.agent.plugin.spring.getbean.ProxyReplacer.register($0, $_,new Class[]{"
					+ methodParamTypes.substring(0, methodParamTypes.length() - 2) + "}, $args);}");
		}
		
	}
	
	/**
	 * Disable cache usage in FastClass.Generator to avoid 'IllegalArgumentException: Protected method' exceptions
	 * 
	 * @param ctClass
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.springframework.cglib.reflect.FastClass.Generator")
	public static void replaceSpringFastClassGenerator(CtClass ctClass) throws NotFoundException,
			CannotCompileException {
		CtConstructor[] constructors = ctClass.getConstructors();
		for (CtConstructor ctConstructor : constructors) {
			ctConstructor.insertAfter("setUseCache(false);");
		}
	}
	
	/**
	 * Disable cache usage in FastClass.Generator to avoid 'IllegalArgumentException: Protected method' exceptions
	 * 
	 * @param ctClass
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "net.sf.cglib.reflect.FastClass.Generator")
	public static void replaceCglibFastClassGenerator(CtClass ctClass) throws NotFoundException, CannotCompileException {
		CtConstructor[] constructors = ctClass.getConstructors();
		for (CtConstructor ctConstructor : constructors) {
			ctConstructor.insertAfter("setUseCache(false);");
		}
	}
}
