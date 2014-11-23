package org.hotswap.agent.plugin.proxy;

import java.io.ByteArrayInputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.util.Map;
import java.util.UUID;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.signature.ClassfileSignatureComparer;

/**
 * Transforms proxy instances.
 * 
 * Takes 1 or 2 redefinition events to transform a proxy unless instructed to add a third step through the public static
 * field addThirdStep.
 * 
 * With a 2 step transformation, the new Proxy is created with original App classloader instances (both the generator
 * and parameters are reused, their classes untouched). During the first step it is checked if the signature has
 * changed. If it has, a second redefinition event is triggered.
 * 
 * During the second step, the new bytecode is generated. Static field initialization code is added to the beginning of
 * methods along with a new randomly named static field holding state of initilization.
 * 
 * 1 step transformation is usually done using a new ParentLastClassLoader instance. Needs a specific implementation for
 * the method handleNewClass, to skip the second event
 * 
 * @author Erki Ehtla
 * 
 */
public abstract class AbstractProxyTransformer {
	
	private static final AgentLogger LOGGER = AgentLogger.getLogger(AbstractProxyTransformer.class);
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	
	protected Class<?> classBeingRedefined;
	protected byte[] classfileBuffer;
	protected Map<Class<?>, TransformationState> transformationStates;
	protected String javaClassName;
	protected ClassPool cp;
	public static boolean addThirdStep = false;
	
	/**
	 * 
	 /**
	 * 
	 * @param classBeingRedefined
	 * @param cc
	 *            CtClass from classfileBuffer
	 * @param cp
	 * @param classfileBuffer
	 *            new definition of Class<?>
	 * @param transformationStates
	 *            holds TransformationState for multistep classredefinition
	 */
	public AbstractProxyTransformer(Class<?> classBeingRedefined, CtClass cc, ClassPool cp, byte[] classfileBuffer,
			Map<Class<?>, TransformationState> transformationStates) {
		this.javaClassName = cc.getName();
		this.classBeingRedefined = classBeingRedefined;
		this.classfileBuffer = classfileBuffer;
		this.transformationStates = transformationStates;
		this.cp = cp;
	}
	
	/**
	 * Transforms the classBeingRedefined if neccessary
	 * 
	 * @return bytecode of the new Class
	 * @throws IllegalClassFormatException
	 */
	public byte[] transform() throws IllegalClassFormatException {
		try {
			if (classBeingRedefined == null || !isProxy()) {
				return classfileBuffer;
			}
			return transformRedefine();
		} catch (Exception e) {
			removeClassState();
			LOGGER.error("Error transforming", e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Checks if there were changes that require the redefinition of the proxy
	 * 
	 * @return
	 */
	private boolean isTransformingNeeded() {
		return ClassfileSignatureComparer.isPoolClassOrParentDifferent(classBeingRedefined, cp);
	}
	
	/**
	 * Check if the redefined Class is a supported proxy
	 * 
	 * @return
	 * @throws Exception
	 */
	protected abstract boolean isProxy() throws Exception;
	
	/**
	 * Handles the current transformation state
	 * 
	 * @return
	 * @throws Exception
	 */
	protected byte[] transformRedefine() throws Exception {
		switch (getTransformationstate()) {
			case NEW:
				if (!isTransformingNeeded()) {
					return classfileBuffer;
				}
				return handleNewClass();
			case WAITING:
				classfileBuffer = generateNewProxyClass();
				if (addThirdStep) {
					setClassAsFinished();
					scheduleRedefinition();
				} else
					removeClassState();
				return classfileBuffer;
			case FINISHED:
				removeClassState();
				return classfileBuffer;
			default:
				throw new RuntimeException("Unhandeled TransformationState!");
		}
	}
	
	protected byte[] handleNewClass() throws Exception {
		setClassAsWaiting();
		// We can't do the transformation in this event, because we can't see the changes in the class
		// definitons. Schedule a new redefinition event.
		scheduleRedefinition();
		return classfileBuffer;
	}
	
	/**
	 * Builds a new Proxy class with initialization code, which is executed during the first call on any method
	 * 
	 * @return bytecode of the new Class
	 * @throws Exception
	 */
	protected byte[] generateNewProxyClass() throws Exception {
		byte[] newByteCode = getNewByteCode();
		
		CtClass cc = getCtClass(newByteCode);
		String random = generateRandomString();
		String initFieldName = INIT_FIELD_PREFIX + random;
		addStaticInitStateField(cc, initFieldName);
		
		String method = getInitCall(cc, random);
		
		addInitCallToMethods(cc, initFieldName, method);
		byte[] bytecode = cc.toBytecode();
		LOGGER.reload("Class '{}' has been reloaded.", javaClassName);
		return bytecode;
	}
	
	/**
	 * Makes a CtClass out of the bytecode
	 * 
	 * @param newByteCode
	 * @return CtClass from newByteCode
	 * @throws Exception
	 */
	protected CtClass getCtClass(byte[] newByteCode) throws Exception {
		return cp.makeClass(new ByteArrayInputStream(newByteCode), false);
	}
	
	/**
	 * Builds the Java code String which should be executed to initialize the proxy
	 * 
	 * @param cc
	 *            CtClass from new definition
	 * @param random
	 *            randomly generated String
	 * @return Java code to call init the proxy
	 */
	protected abstract String getInitCall(CtClass cc, String random) throws Exception;
	
	/**
	 * @return unchanged bytecode of the new Proxy Class. Does not contain any initialization.
	 * @throws Exception
	 */
	protected abstract byte[] getNewByteCode() throws Exception;
	
	/**
	 * @return Current transformation state of the classBeingRedefined
	 */
	protected TransformationState getTransformationstate() {
		TransformationState transformationState = transformationStates.get(classBeingRedefined);
		if (transformationState == null)
			transformationState = TransformationState.NEW;
		return transformationState;
	}
	
	protected String generateRandomString() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
	/**
	 * Adds the initCall as Java code to all the non static methods of the class. The initialization is only done if
	 * clinitFieldName is false. Responsibility to set the clinitFieldName is on the initCall.
	 * 
	 * @param cc
	 *            CtClass to be modified
	 * @param clinitFieldName
	 *            field name in CtClass
	 * @param initCall
	 *            Java code to initialize the Proxy
	 * @throws Exception
	 */
	protected void addInitCallToMethods(CtClass cc, String clinitFieldName, String initCall) throws Exception {
		CtMethod[] methods = cc.getDeclaredMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
				ctMethod.insertBefore("if(!" + clinitFieldName + "){synchronized(this){if(!" + clinitFieldName + "){"
						+ initCall + "}}}");
			}
		}
	}
	
	/**
	 * Adds static boolean field to the class
	 * 
	 * @param cc
	 *            CtClass to be modified
	 * @param clinitFieldName
	 *            field name in CtClass
	 * @throws Exception
	 */
	protected void addStaticInitStateField(CtClass cc, String clinitFieldName) throws Exception {
		CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
		f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		// init value "true" will be inside clinit, so the field wont actually be initialized to this
		cc.addField(f, "true");
	}
	
	/**
	 * Generate new redefinition event for current classBeingRedefined
	 */
	protected void scheduleRedefinition() {
		new Thread(new RedefinitionScheduler(this)).start();
	}
	
	/**
	 * Set classBeingRedefined as waiting
	 * 
	 * @return
	 */
	protected TransformationState setClassAsWaiting() {
		return transformationStates.put(classBeingRedefined, TransformationState.WAITING);
	}
	
	/**
	 * Set classBeingRedefined as finished
	 * 
	 * @return
	 */
	protected TransformationState setClassAsFinished() {
		return transformationStates.put(classBeingRedefined, TransformationState.FINISHED);
	}
	
	/**
	 * Remove any state associated with classBeingRedefined
	 * 
	 * @return
	 */
	protected TransformationState removeClassState() {
		return transformationStates.remove(classBeingRedefined);
	}
	
	/**
	 * The Class this instance is redefining
	 * 
	 * @return
	 */
	public Class<?> getClassBeingRedefined() {
		return classBeingRedefined;
	}
	
	/**
	 * Bytecode of the Class this instance is redefining.
	 * 
	 * @return
	 */
	public byte[] getClassfileBuffer() {
		return classfileBuffer;
	}
}