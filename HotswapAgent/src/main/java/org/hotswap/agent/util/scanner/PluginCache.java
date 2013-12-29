package org.hotswap.agent.util.scanner;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Jiri Bubnik
 * @deprecated not used, probably remove in favor of PluginRegistry
 */
@Deprecated
public class PluginCache {

    public static final String PLUGIN_PATH = "org/hotswap/agent/plugin";

    private Map<ClassLoader, Set<CtClass>> pluginDefs = new HashMap<ClassLoader, Set<CtClass>>();

    Scanner scanner = new ClassPathScanner();

    public Set<CtClass> getPlugins(ClassLoader classLoader) {
        if (pluginDefs.containsKey(classLoader))
            return pluginDefs.get(classLoader);
        else
            return Collections.emptySet();
    }

    public Set<CtClass> scanPlugins(ClassLoader classLoader) throws IOException {
        if (!pluginDefs.containsKey(classLoader)) {
            synchronized (pluginDefs) {
                if (!pluginDefs.containsKey(classLoader)) {
                    final Set<CtClass> plugins = new HashSet<CtClass>();
                    final ClassPool classPool = ClassPool.getDefault();

                    scanner.scan(getClass().getClassLoader(), PLUGIN_PATH, new ScannerVisitor() {
                        @Override
                        public void visit(InputStream file) throws IOException {
                            plugins.add(classPool.makeClass(file));
                        }
                    });
                }
            }
        }

        return pluginDefs.get(classLoader);
    }
}
