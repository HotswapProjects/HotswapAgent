package org.hotswap.agent.plugin.proxy;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.UnmodifiableClassException;
import java.util.Map;
import java.util.UUID;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.hscglib.CglibProxyTransformer;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureRecorder;

/**
 * @author Erki Ehtla
 * 
 */
public abstract class AbstractProxyTransformer {
	private static AgentLogger LOGGER = AgentLogger.getLogger(CglibProxyTransformer.class);
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	
	// protected Instrumentation inst;
	protected ClassLoader loader;
	protected String className;
	protected Class<?> classBeingRedefined;
	protected byte[] classfileBuffer;
	protected Map<Class<?>, TransformationState> transformationStates;
	protected String javaClassName;
	public static boolean addThirdStep = false;
	
	public AbstractProxyTransformer(ClassLoader loader, String className, Class<?> classBeingRedefined,
			byte[] classfileBuffer, Map<Class<?>, TransformationState> transformationStates) {
		this.loader = loader;
		this.className = className;
		this.javaClassName = Descriptor.toJavaName(className);
		this.classBeingRedefined = classBeingRedefined;
		this.classfileBuffer = classfileBuffer;
		this.transformationStates = transformationStates;
	}
	
	public byte[] transform() throws IllegalClassFormatException {
		try {
			if (classBeingRedefined == null || !isProxy()) {
				return null;
			}
			return transformRedefine();
		} catch (Exception e) {
			removeClassState();
			LOGGER.error("", e);
			throw new RuntimeException(e);
		}
	}
	
	private boolean isTransformingNeeded() {
		return ClassfileSignatureRecorder.hasSuperClassOrInterfaceChanged(classBeingRedefined);
	}
	
	protected abstract boolean isProxy() throws Exception;
	
	protected byte[] transformRedefine() throws Exception {
		switch (getTransformationstate()) {
			case NEW:
				if (!isTransformingNeeded()) {
					return null;
				}
				setClassAsWaiting();
				// We can't do the transformation in this event, because we can't see the changes in the class
				// definitons. Schedule a new redefinition event.
				scheduleRedefinition();
				return null;
			case WAITING:
				byte[] newProxyClass = generateNewProxyClass();
				if (addThirdStep) {
					setClassAsFinished();
					scheduleRedefinition();
				} else
					removeClassState();
				LOGGER.reload("Class '{}' has been reloaded.", Descriptor.toJavaName(className));
				return newProxyClass;
			case FINISHED:
				removeClassState();
				return classfileBuffer;
			default:
				throw new RuntimeException("Unhandeled TransformationState!");
		}
	}
	
	protected byte[] generateNewProxyClass() throws Exception {
		byte[] newByteCode = getNewByteCode();
		
		CtClass cc = getCtClass(newByteCode);
		String random = generateRandomString();
		String initFieldName = INIT_FIELD_PREFIX + random;
		addStaticInitStateField(cc, initFieldName);
		
		String method = getInitCall(cc, random);
		
		addInitCallToMethods(cc, initFieldName, method);
		
		return cc.toBytecode();
	}
	
	protected CtClass getCtClass(byte[] newByteCode) throws Exception {
		return ProxyTransformationUtils.getClassPool().makeClass(new ByteArrayInputStream(newByteCode), false);
	}
	
	protected abstract String getInitCall(CtClass cc, String random) throws Exception;
	
	protected abstract byte[] getNewByteCode() throws Exception;
	
	protected TransformationState getTransformationstate() {
		TransformationState transformationState = transformationStates.get(classBeingRedefined);
		if (transformationState == null)
			transformationState = TransformationState.NEW;
		return transformationState;
	}
	
	protected String generateRandomString() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
	protected void addInitCallToMethods(CtClass cc, String clinitFieldName, String initCall) throws Exception {
		CtMethod[] methods = cc.getDeclaredMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
				ctMethod.insertBefore("if(!" + clinitFieldName + "){" + initCall + "}");
			}
		}
	}
	
	protected void addStaticInitStateField(CtClass cc, String clinitFieldName) throws Exception {
		CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
		f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		// init value "true" will be inside clinit, so the field wont actually be initialized to this
		cc.addField(f, "true");
	}
	
	protected void scheduleRedefinition() {
		new Thread() {
			@Override
			public void run() {
				try {
					PluginManager.getInstance().getInstrumentation()
							.redefineClasses(new ClassDefinition(classBeingRedefined, classfileBuffer));
				} catch (ClassNotFoundException | UnmodifiableClassException e) {
					removeClassState();
					throw new RuntimeException(e);
				}
			}
		}.start();
	}
	
	protected TransformationState setClassAsWaiting() {
		return transformationStates.put(classBeingRedefined, TransformationState.WAITING);
	}
	
	private TransformationState setClassAsFinished() {
		return transformationStates.put(classBeingRedefined, TransformationState.FINISHED);
	}
	
	protected TransformationState removeClassState() {
		return transformationStates.remove(classBeingRedefined);
	}
}