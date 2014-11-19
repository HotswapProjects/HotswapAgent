package org.hotswap.agent.plugin.proxy;

import java.lang.instrument.ClassDefinition;

import org.hotswap.agent.config.PluginManager;

/**
 * RedefinesClass for AbstractProxyTransformer
 * 
 * @author Erki Ehtla
 */
final class RedefinitionScheduler implements Runnable {
	private AbstractProxyTransformer transformer;
	
	public RedefinitionScheduler(AbstractProxyTransformer transformer) {
		this.transformer = transformer;
	}
	
	@Override
	public void run() {
		try {
			PluginManager
					.getInstance()
					.getInstrumentation()
					.redefineClasses(
							new ClassDefinition(transformer.getClassBeingRedefined(), transformer.getClassfileBuffer()));
		} catch (Throwable t) {
			transformer.removeClassState();
			throw new RuntimeException(t);
		}
	}
}