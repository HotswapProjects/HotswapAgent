package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import org.hotswap.agent.plugin.proxy.ParentLastClassLoader;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Creates a new Cglib proxy with an Enhancer. Uses Classes loaded with a new ParentLastClassLoader instance.
 * 
 * @author Erki Ehtla
 * 
 */
public class EnhancerCreater {
	
	private GeneratorParams param;
	private ClassLoader classLoader;
	private Class<?> enhancerClass;
	private Object enhancer;
	private Class<?> abstractGeneratorClass;
	
	/**
	 * 
	 * @param param
	 *            Parameters of the previous bytecode generation
	 * @param classLoader
	 *            Enhancer classloader
	 */
	public EnhancerCreater(GeneratorParams param, ClassLoader classLoader) {
		this.param = param;
		this.classLoader = new ParentLastClassLoader(classLoader);
		this.enhancer = param.getParam();
		this.enhancerClass = enhancer.getClass();
		this.abstractGeneratorClass = enhancerClass.getSuperclass();
	}
	
	private static class FieldState {
		public FieldState(Field field, Object fieldValue) {
			this.field = field;
			this.fieldValue = fieldValue;
		}
		
		private Field field;
		private Object fieldValue;
	}
	
	/**
	 * Generates bytecode for the proxy class
	 * 
	 * @return bytecode of the new proxy class
	 * @throws Exception
	 */
	public byte[] create() throws Exception {
		Collection<FieldState> oldClassValues = getFieldValuesWithClasses();
		ClassLoader oldClassLoader = (ClassLoader) ReflectionHelper.get(enhancer, "classLoader");
		Boolean oldUseCache = (Boolean) ReflectionHelper.get(enhancer, "useCache");
		try {
			ReflectionHelper.set(enhancer, abstractGeneratorClass, "classLoader", classLoader);
			ReflectionHelper.set(enhancer, abstractGeneratorClass, "useCache", Boolean.FALSE);
			setFieldValuesWithNewLoadedClasses(oldClassValues);
			
			return (byte[]) ReflectionHelper.invoke(param.getGenerator(), param.getGenerator().getClass(), "generate",
					new Class[] { getGeneratorInterfaceClass() }, enhancer);
		} finally {
			ReflectionHelper.set(enhancer, abstractGeneratorClass, "classLoader", oldClassLoader);
			ReflectionHelper.set(enhancer, abstractGeneratorClass, "useCache", oldUseCache);
			setFieldValues(oldClassValues);
		}
	}
	
	/**
	 * 
	 * @return ClassGenerator interface Class instance
	 */
	private Class<?> getGeneratorInterfaceClass() {
		Class<?>[] interfaces = abstractGeneratorClass.getInterfaces();
		for (Class<?> iClass : interfaces) {
			if (iClass.getName().endsWith(".ClassGenerator"))
				return iClass;
		}
		return null;
	}
	
	private void setFieldValues(Collection<FieldState> fieldStates) throws IllegalAccessException {
		for (FieldState fieldState : fieldStates) {
			fieldState.field.set(enhancer, fieldState.fieldValue);
		}
	}
	
	private void setFieldValuesWithNewLoadedClasses(Collection<FieldState> fieldStates) throws IllegalAccessException,
			ClassNotFoundException {
		for (FieldState fieldState : fieldStates) {
			fieldState.field.set(enhancer, loadFromClassloader(fieldState.fieldValue));
		}
	}
	
	private Collection<FieldState> getFieldValuesWithClasses() throws IllegalAccessException {
		Collection<FieldState> classValueFields = new ArrayList<FieldState>();
		
		Field[] fields = enhancerClass.getDeclaredFields();
		for (Field field : fields) {
			if (!Modifier.isStatic(field.getModifiers())
					&& (field.getType().isInstance(Class.class) || field.getType().isInstance(Class[].class))) {
				field.setAccessible(true);
				classValueFields.add(new FieldState(field, field.get(enhancer)));
			}
		}
		return classValueFields;
	}
	
	private Object loadFromClassloader(Object fieldState) throws ClassNotFoundException {
		if (fieldState instanceof Class[]) {
			Class<?>[] classes = ((Class[]) fieldState);
			Class<?>[] newClasses = new Class[classes.length];
			for (int i = 0; i < classes.length; i++) {
				newClasses[i] = classLoader.loadClass(classes[i].getName());
			}
			return newClasses;
		} else {
			return classLoader.loadClass(((Class<?>) fieldState).getName());
		}
	}
}
