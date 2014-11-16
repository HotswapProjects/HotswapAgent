package org.hotswap.agent.plugin.proxy;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.UnmodifiableClassException;

import org.hotswap.agent.config.PluginManager;

/**
 * 
 * @author Erki Ehtla
 * 
 */
final class RedefinitionScheduler implements Runnable {
	private AbstractProxyTransformer i;
	
	public RedefinitionScheduler(AbstractProxyTransformer i) {
		this.i = i;
	}
	
	@Override
	public void run() {
		try {
			PluginManager.getInstance().getInstrumentation()
					.redefineClasses(new ClassDefinition(i.getClassBeingRedefined(), i.getClassfileBuffer()));
		} catch (ClassNotFoundException | UnmodifiableClassException e) {
			throw new RuntimeException(e);
		} finally {
			i.removeClassState();
		}
	}
}