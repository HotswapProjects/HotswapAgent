package org.hotswap.agent.plugin.proxy;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import org.hotswap.agent.annotation.Init;

/**
 * Schedules a new redefiniton event for MultistepProxyTransformer
 * 
 * @author Erki Ehtla
 */
public final class RedefinitionScheduler implements Runnable {
	private MultistepProxyTransformer transformer;
	
	@Init
	private static Instrumentation instrumentation;
	
	public RedefinitionScheduler(MultistepProxyTransformer transformer) {
		this.transformer = transformer;
	}
	
	@Override
	public void run() {
		try {
			instrumentation.redefineClasses(new ClassDefinition(transformer.getClassBeingRedefined(), transformer
					.getClassfileBuffer()));
		} catch (Throwable t) {
			transformer.removeClassState();
			throw new RuntimeException(t);
		}
	}
	
	public static void schedule(MultistepProxyTransformer multistepProxyTransformer) {
		new Thread(new RedefinitionScheduler(multistepProxyTransformer)).start();
	}
}