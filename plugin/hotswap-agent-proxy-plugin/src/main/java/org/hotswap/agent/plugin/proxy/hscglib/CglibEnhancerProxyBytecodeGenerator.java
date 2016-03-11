package org.hotswap.agent.plugin.proxy.hscglib;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import org.hotswap.agent.plugin.proxy.ParentLastClassLoader;
import org.hotswap.agent.plugin.proxy.ProxyBytecodeGenerator;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * Creates new bytecode for a Cglib Enhancer proxy. Uses Classes loaded with a new instance of a ParentLastClassLoader.
 * 
 * @author Erki Ehtla
 * 
 */

public class CglibEnhancerProxyBytecodeGenerator implements ProxyBytecodeGenerator {
	
	private GeneratorParams param;
	private ClassLoader classLoader;
	private Class<?> generatorClass;
	private Object generator;
	private Class<?> abstractGeneratorClass;
	
	/**
	 * 
	 * @param param
	 *            Parameters of the previous bytecode generation call
	 * @param classLoader
	 *            Enhancer classloader
	 */
	public CglibEnhancerProxyBytecodeGenerator(GeneratorParams param, ClassLoader classLoader) {
		this.param = param;
		this.classLoader = new ParentLastClassLoader(classLoader);
		this.generator = param.getParam();
		this.generatorClass = generator.getClass();
		this.abstractGeneratorClass = generatorClass.getSuperclass();
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
	@Override
	public byte[] generate() throws Exception {
		Collection<FieldState> oldClassValues = getFieldValuesWithClasses();
		ClassLoader oldClassLoader = (ClassLoader) ReflectionHelper.get(generator, "classLoader");
		Boolean oldUseCache = (Boolean) ReflectionHelper.get(generator, "useCache");
		try {
			ReflectionHelper.set(generator, abstractGeneratorClass, "classLoader", classLoader);
			ReflectionHelper.set(generator, abstractGeneratorClass, "useCache", Boolean.FALSE);
			setFieldValuesWithNewLoadedClasses(oldClassValues);
			byte[] invoke = (byte[]) ReflectionHelper.invoke(param.getGenerator(), param.getGenerator().getClass(),
					"generate", new Class[] { getGeneratorInterfaceClass() }, generator);
			return invoke;
		} finally {
			ReflectionHelper.set(generator, abstractGeneratorClass, "classLoader", oldClassLoader);
			ReflectionHelper.set(generator, abstractGeneratorClass, "useCache", oldUseCache);
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
			fieldState.field.set(generator, fieldState.fieldValue);
		}
	}
	
	/**
	 * replaces fields with Class values with new classes loaded by a ParentLastClassLoader
	 * 
	 * @param fieldStates
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 */
	private void setFieldValuesWithNewLoadedClasses(Collection<FieldState> fieldStates) throws IllegalAccessException,
			ClassNotFoundException {
		for (FieldState fieldState : fieldStates) {
			fieldState.field.set(generator, loadFromClassloader(fieldState.fieldValue));
		}
	}
	
	private Collection<FieldState> getFieldValuesWithClasses() throws IllegalAccessException {
		Collection<FieldState> classValueFields = new ArrayList<FieldState>();
		
		Field[] fields = generatorClass.getDeclaredFields();
		for (Field field : fields) {
			if (!Modifier.isStatic(field.getModifiers())
					&& (field.getType().isInstance(Class.class) || field.getType().isInstance(Class[].class))) {
				field.setAccessible(true);
				classValueFields.add(new FieldState(field, field.get(generator)));
			}
		}
		return classValueFields;
	}
	
	/**
	 * Load classes from ParentLastClassLoader
	 * 
	 * @param fieldState
	 * @return
	 * @throws ClassNotFoundException
	 */
	private Object loadFromClassloader(Object fieldState) throws ClassNotFoundException {
		if (fieldState instanceof Class[]) {
			Class<?>[] classes = ((Class[]) fieldState);
			Class<?>[] newClasses = new Class[classes.length];
			for (int i = 0; i < classes.length; i++) {
				Class<?> loadClass = classLoader.loadClass(classes[i].getName());
				newClasses[i] = loadClass;
			}
			return newClasses;
		} else {
			return classLoader.loadClass(((Class<?>) fieldState).getName());
		}
	}
}
