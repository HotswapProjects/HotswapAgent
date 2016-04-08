package org.hotswap.agent.plugin.hibernate3.session.util;

import java.lang.reflect.Modifier;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewConstructor;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.bytecode.AccessFlag;
import org.hotswap.agent.plugin.hibernate3.session.proxy.ReInitializableHelper;

/**
 * Utility functions for instrumenting classes
 * 
 * @author alpapad@gmail.com
 *
 */
public class ProxyUtil {

	public static void addMethod(ClassLoader classLoader, ClassPool classPool, CtClass clazz, String returns, String method, String[] args) throws CannotCompileException {
		try{
			CtMethod oldMethod = clazz.getDeclaredMethod(method, getParamTypes(classPool, args));
			if (oldMethod != null) {
				oldMethod.setName('_' + method);
			}
		} catch(NotFoundException e) {
			
		}
		
		StringBuilder body = new StringBuilder();
		body.append(" public ").append(returns).append(' ').append(method).append('(').append(getMethodArgs(args)).append(')').append("{\n");
		body.append("    ");
		if (!"void".equals(returns)) {
			body.append("return ");
		}
		body.append(ReInitializableHelper.class.getName()).append('.').append(method).append('(').append(getCallArgs(args)).append(')').append(";\n");
		body.append('}');
		
		CtMethod newMethod = CtNewMethod.make(body.toString(), clazz);
		clazz.addMethod(newMethod);
	}
    
	
    private static CtClass[] getParamTypes(ClassPool classPool, String args[]) throws NotFoundException{
    	if(args == null || args.length ==0) {
    		return new CtClass[0];
    	}else {
    		CtClass[] res = new CtClass[args.length];
    		for(int i=0;i<args.length;i++) {
    			res[i] =  classPool.get(args[i]);
    		}
    		return res;
    	}
    }
    
    private static String getMethodArgs(String args[]){
    	if(args == null || args.length ==0) {
    		return "";
    	}else {
    		StringBuilder l = new StringBuilder();
    		
    		for(int i=0;i<args.length;i++) {
    			
    			if(i > 0){
    				l.append(',');
    			}
    			l.append(args[i]).append(' ').append("$arg").append(i);
    		}
    		return l.toString();
    	}
    }
    
    private static String getCallArgs(String args[]){
    	if(args == null || args.length ==0) {
    		return "this";
    	}else {
    		StringBuilder l = new StringBuilder("this");
    		for(int i=0;i<args.length;i++) {
   				l.append(',').append("$arg").append(i);
    		}
    		return l.toString();
    	}
    }
    
    
	public static void ensureProxyable(CtClass clazz) throws CannotCompileException{
		int flags = clazz.getClassFile().getAccessFlags();
    	flags = AccessFlag.setPublic(flags);
    	flags = AccessFlag.clear(flags, AccessFlag.FINAL);
        clazz.getClassFile().setAccessFlags(flags);
        try{
        	CtConstructor ct = clazz.getDeclaredConstructor(new CtClass[]{});
        	if(Modifier.isPrivate(ct.getModifiers())) {
        		ct.setModifiers(AccessFlag.setProtected(ct.getModifiers()));
        	}
        } catch(NotFoundException ex) {
	        CtConstructor c = CtNewConstructor.make("protected " + clazz.getSimpleName() + "() {}", clazz);
	        clazz.addConstructor(c);       
        }
	}
	
	public static void makeProxy(CtClass proxy, CtClass proxied, ClassPool classPool, ClassLoader classLoader) throws Exception {
		proxy.setSuperclass(proxied);
		
		for (CtMethod m : proxied.getMethods()) {
			int mod = m.getModifiers();

			if (!Modifier.isFinal(mod) //
					&& !Modifier.isStatic(mod) //
					&& isVisible(mod, proxied.getPackageName(), m) //
					&& (!m.getDeclaringClass().getName().equals("java.lang.Object"))) {
				String meth = toProxy(m);
				CtMethod newMethod = CtNewMethod.make(meth, proxy);
				proxy.addMethod(newMethod);
			}
		}
		
		for (CtClass i : proxied.getInterfaces()) {
			proxy.addInterface(i);
		}
	}

	private static String toProxy(CtMethod m) throws NotFoundException {
		StringBuilder r = new StringBuilder("public ");
		String ret = m.getReturnType().getName();

		r.append(ret).append(' ');
		r.append(m.getName()).append('(');
		for (int i = 0; i < m.getParameterTypes().length; i++) {
			r.append(m.getParameterTypes()[i].getName()).append(" a").append(i);
			if (i < m.getParameterTypes().length - 1) {
				r.append(',');
			}
		}
		r.append("){\n ");
		if (!"void".equals(ret)) {
			r.append("return");
		}
		r.append(" currentInstance.").append(m.getName()).append('(');
		for (int i = 0; i < m.getParameterTypes().length; i++) {
			r.append(" a").append(i);
			if (i < m.getParameterTypes().length - 1) {
				r.append(',');
			}
		}
		r.append(");\n");
		r.append("}\n");

		return r.toString();
	}

	/**
	 * Returns true if the method is visible from the package.
	 *
	 * @param mod
	 *            the modifiers of the method.
	 */
	private static boolean isVisible(int mod, String from, CtMethod meth) {
		if ((mod & Modifier.PRIVATE) != 0) {
			return false;
		} else if ((mod & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
			return true;
		} else {
			String p = getPackageName(from);
			String q = getPackageName(meth.getDeclaringClass().getName());
			if (p == null) {
				return q == null;
			} else {
				return p.equals(q);
			}
		}
	}

	private static String getPackageName(String name) {
		int i = name.lastIndexOf('.');
		if (i < 0) {
			return null;
		} else {
			return name.substring(0, i);
		}
	}
}
