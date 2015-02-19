package org.hotswap.agent.plugin.weld.command;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.WeldPlugin;
import org.hotswap.agent.util.ReflectionHelper;

public class RecreateProxyClassCommand extends MergeableCommand {
	private static AgentLogger LOGGER = AgentLogger.getLogger(WeldPlugin.class);
	private Map<Object, Object> registeredBeans;
	private Map<String, CtClass> hotswappedCtClassMap;
	private ClassLoader appClassLoader;

	public RecreateProxyClassCommand(ClassLoader appClassLoader,
			Map<String, CtClass> hotswappedCtClassMap,
			Map<Object, Object> registeredBeans) {
		this.appClassLoader = appClassLoader;
		this.hotswappedCtClassMap = hotswappedCtClassMap;
		this.registeredBeans = registeredBeans;
	}

	@Override
	public void executeCommand() {
		synchronized (registeredBeans) {
			synchronized (hotswappedCtClassMap) {
				reloadBeanClass();
			}
		}
	}

	public void reloadBeanClass() {
		try {
			// Hack to re-generate the weld client proxies
			for (Entry<Object, Object> entry : registeredBeans.entrySet()) {
				Class<?> proxyFactoryClass = appClassLoader.loadClass("org.jboss.weld.bean.proxy.ProxyFactory");
				Class<?> beanClass = appClassLoader.loadClass("javax.enterprise.inject.spi.Bean");
				final Object proxyFactory = entry.getValue();
				final Object bean = entry.getKey();
				for (CtClass ctClass : hotswappedCtClassMap.values()) {
					Set<Type> types = (Set<Type>) ReflectionHelper.invoke(bean,
							beanClass, "getTypes", new Class[] {});
					if (types.contains(ctClass.getClass())) {
						LOGGER.debug("Recreate proxyClass {} for bean class {}.",
								ctClass.getName(), bean.getClass());
						ReflectionHelper.invoke(proxyFactory,
								proxyFactoryClass, "recreateProxyClass",
								new Class[] {});
					}
				}
			}
		} catch (ClassNotFoundException e) {

		}
	}

	private Class loadClass(String name) throws ClassNotFoundException {
		return appClassLoader.loadClass(name);
	}
}
