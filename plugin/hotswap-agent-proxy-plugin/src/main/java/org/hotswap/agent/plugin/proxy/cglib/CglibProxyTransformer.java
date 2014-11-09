package org.hotswap.agent.plugin.proxy.cglib;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.ProxyTransformationUtils;
import org.hotswap.agent.plugin.proxy.TransformationState;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureRecorder;

/**
 * @author Erki Ehtla
 * 
 */
public class CglibProxyTransformer {
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	protected static final ClassPool classPool = ProxyTransformationUtils.getClassPool();
	protected static Map<Class<?>, TransformationState> transformationStates = new ConcurrentHashMap<Class<?>, TransformationState>();
	// used during testing to ensure that transformation has completed and the result returned by isReloadingInProgress
	// is valid
	public static boolean addThirdStep = false;
	private static AgentLogger LOGGER = AgentLogger.getLogger(CglibProxyTransformer.class);
	
	// @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE, skipSynthetic = false)
	public static byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		LOGGER.error(Thread.currentThread().getName());
		try {
			if (!isProxy(className, classBeingRedefined, classfileBuffer)) {
				return null;
			}
			return transformRedefine(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
		} catch (Exception e) {
			removeClassState(classBeingRedefined);
			LOGGER.error("error redefining proxy", e);
			throw new RuntimeException(e);
		}
	}
	
	private static boolean isTransformingNeeded(Class<?> classBeingRedefined) {
		Class<?> superclass = classBeingRedefined.getSuperclass();
		if (superclass != null && ClassfileSignatureRecorder.hasClassChanged(superclass))
			return true;
		Class<?>[] interfaces = classBeingRedefined.getInterfaces();
		for (Class<?> clazz : interfaces) {
			if (ClassfileSignatureRecorder.hasClassChanged(clazz))
				return true;
		}
		return false;
	}
	
	private static byte[] transformRedefine(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws Exception {
		switch (getTransformationstate(classBeingRedefined)) {
			case NEW:
				if (!isTransformingNeeded(classBeingRedefined)) {
					return null;
				}
				setClassAsWaiting(classBeingRedefined);
				// We can't do the transformation in this event, because we can't see the changes in the class
				// definitons. Schedule a new redefinition event.
				scheduleRedefinition(classBeingRedefined, classfileBuffer);
				return null;
			case WAITING:
				byte[] newProxyClass = generateNewProxyClass(loader, className, classBeingRedefined);
				if (addThirdStep) {
					setClassAsFinished(classBeingRedefined);
					scheduleRedefinition(classBeingRedefined, newProxyClass);
				} else
					removeClassState(classBeingRedefined);
				LOGGER.reload("Class '{}' has been reloaded.", ProxyTransformationUtils.getClassName(className));
				return newProxyClass;
			case FINISHED:
				removeClassState(classBeingRedefined);
				return classfileBuffer;
			default:
				throw new RuntimeException("Unhandeled TransformationState!");
		}
	}
	
	private static byte[] generateNewProxyClass(ClassLoader loader, String className, Class<?> classBeingRedefined)
			throws Exception {
		
		byte[] newByteCode = getNewByteCode(loader, className, classBeingRedefined);
		
		CtClass cc = getCtClass(newByteCode, className);
		String random = generateRandomString();
		String initFieldName = INIT_FIELD_PREFIX + random;
		addStaticInitStateField(cc, initFieldName);
		
		String method = getInitCall(cc, random);
		
		addInitCallToMethods(cc, initFieldName, method);
		
		return cc.toBytecode();
	}
	
	private static TransformationState getTransformationstate(final Class<?> classBeingRedefined) {
		TransformationState transformationState = transformationStates.get(classBeingRedefined);
		if (transformationState == null)
			transformationState = TransformationState.NEW;
		return transformationState;
	}
	
	private static String generateRandomString() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
	private static void addInitCallToMethods(CtClass cc, String clinitFieldName, String initCall) throws Exception {
		CtMethod[] methods = cc.getDeclaredMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
				ctMethod.insertBefore("if(!" + clinitFieldName + "){" + initCall + "}");
			}
		}
	}
	
	private static void addStaticInitStateField(CtClass cc, String clinitFieldName) throws Exception {
		CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
		f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		// init value "true" will be inside clinit, so the field wont actually be initialized to this
		cc.addField(f, "true");
	}
	
	private static void scheduleRedefinition(final Class<?> classBeingRedefined, final byte[] classfileBuffer) {
		new Thread() {
			@Override
			public void run() {
				try {
					PluginManager.getInstance().getInstrumentation()
							.redefineClasses(new ClassDefinition(classBeingRedefined, classfileBuffer));
				} catch (ClassNotFoundException | UnmodifiableClassException e) {
					removeClassState(classBeingRedefined);
					throw new RuntimeException(e);
				}
			}
		}.start();
	}
	
	private static TransformationState setClassAsWaiting(Class<?> classBeingRedefined) {
		return transformationStates.put(classBeingRedefined, TransformationState.WAITING);
	}
	
	private static TransformationState removeClassState(Class<?> classBeingRedefined) {
		return transformationStates.remove(classBeingRedefined);
	}
	
	private static TransformationState setClassAsFinished(Class<?> classBeingRedefined) {
		return transformationStates.put(classBeingRedefined, TransformationState.FINISHED);
	}
	
	private static boolean isProxy(String className, Class<?> classBeingRedefined, byte[] classfileBuffer) {
		return GeneratorParametersRecorder.getGeneratorParams().containsKey(className.replaceAll("/", "."));
	}
	
	private static String getInitCall(CtClass cc, String random) throws Exception {
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
	
	private static byte[] getNewByteCode(ClassLoader loader, String className, Class<?> classBeingRedefined)
			throws Exception {
		GeneratorParams param = GeneratorParametersRecorder.getGeneratorParams().get(
				ProxyTransformationUtils.getClassName(className));
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		
		byte[] invoke = (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
		return invoke;
	}
	
	private static CtClass getCtClass(byte[] newByteCode, String className) throws Exception {
		// can use get because generator parameters recorder has already loaded it to the class pool
		return classPool.get(ProxyTransformationUtils.getClassName(className));
	}
	
	private static Method getGenerateMethod(Object generator) {
		Method generateMethod = null;
		Method[] methods = generator.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
				generateMethod = method;
				break;
			}
		}
		return generateMethod;
	}
	
	public static boolean isReloadingInProgress() {
		return transformationStates.size() > 0;
	}
	
}
