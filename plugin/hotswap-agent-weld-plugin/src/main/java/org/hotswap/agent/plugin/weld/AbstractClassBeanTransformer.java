package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class AbstractClassBeanTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(AbstractClassBeanTransformer.class);

	/**
	 * 
	 * @param ctClass
	 * @param classPool
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bean.AbstractClassBean")
	public static void transformAbstractClassBean(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
		CtMethod method = ctClass.getDeclaredMethod("cleanupAfterBoot");
		method.setBody("{ }");
		LOGGER.debug("AbstractClassBean.cleanupAfterBoot patched");
	}
}
