package org.hotswap.agent.plugin.proxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.UnmodifiableClassException;
import java.net.URL;
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
			URL classResource = getClassResource(entry.getKey());
			
			try (OutputStream fileWriter = new FileOutputStream(new File(classResource.toURI()))) {
				fileWriter.write(entry.getValue());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		PluginManager.getInstance().getInstrumentation().redefineClasses(definitions);
	}
	
	@Override
	public void close() throws IOException {
		// Do nothing.
	}
	
	public static URL getClassResource(Class<?> klass) {
		String replace = klass.getName().replace('.', '/') + ".class";
		return klass.getClassLoader().getResource(replace);
	}
}
