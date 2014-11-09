package org.hotswap.agent.plugin.proxy;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.UnmodifiableClassException;
import java.util.Map;

import org.hotswap.agent.config.PluginManager;

public class InstrumentationRedefiner implements Redefiner {
	public void redefineClasses(Map<Class<?>, byte[]> classes) throws ClassNotFoundException,
			UnmodifiableClassException {
		
		if (PluginManager.getInstance().getInstrumentation() == null) {
			throw new IllegalStateException("Instrumentation agent is not properly installed!");
		}
		
		ClassDefinition[] definitions = new ClassDefinition[classes.size()];
		int i = 0;
		for (Map.Entry<Class<?>, byte[]> entry : classes.entrySet()) {
			definitions[i++] = new ClassDefinition(entry.getKey(), entry.getValue());
		}
		PluginManager.getInstance().getInstrumentation().redefineClasses(definitions);
	}
	
	@Override
	public void close() throws IOException {
		// Do nothing.
	}
}
