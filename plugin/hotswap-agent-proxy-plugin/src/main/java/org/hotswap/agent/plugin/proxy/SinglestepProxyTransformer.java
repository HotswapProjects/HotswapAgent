package org.hotswap.agent.plugin.proxy;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Proxy transformations that can be done in one step
 * 
 * @author Erki Ehtla
 * 
 */
public abstract class SinglestepProxyTransformer extends AbstractProxyTransformer {
	private static final AgentLogger LOGGER = AgentLogger.getLogger(SinglestepProxyTransformer.class);
	
	protected byte[] classfileBuffer;
	
	public SinglestepProxyTransformer(Class<?> classBeingRedefined, ClassPool classPool, byte[] classfileBuffer) {
		super(classBeingRedefined, classPool);
		this.classfileBuffer = classfileBuffer;
	}
	
	/**
	 * Handles the current transformation state
	 * 
	 * @return
	 * @throws Exception
	 */
	public byte[] transformRedefine() throws Exception {
		if (!isTransformingNeeded()) {
			return classfileBuffer;
		}
		classfileBuffer = getTransformer().transform(getGenerator().generate());
		LOGGER.reload("Class '{}' has been reloaded.", classBeingRedefined.getName());
		return classfileBuffer;
	}
}
