package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.signature.ClassSignatureComparer;

/**
 * Determines if a full Spring reload is needed. Changes to synthetic and known generated classes are ignored. For other
 * classes, changes to method bodies are ignored.
 *
 * @author Erki Ehtla
 *
 */
public class SpringChangesAnalyzer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringPlugin.class);

    private ClassPool cp;

    public SpringChangesAnalyzer(final ClassLoader classLoader) {
        this.cp = new ClassPool() {

            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }
        };
        cp.appendSystemPath();
        cp.appendClassPath(new LoaderClassPath(classLoader));
    }

    public boolean isReloadNeeded(Class<?> classBeingRedefined, byte[] classfileBuffer) {
        if (classBeingRedefined.isSynthetic() || isSyntheticClass(classBeingRedefined))
            return false;
        return classChangeNeedsReload(classBeingRedefined, classfileBuffer);
    }

    private boolean classChangeNeedsReload(Class<?> classBeingRedefined, byte[] classfileBuffer) {
        CtClass makeClass = null;
        try {
            makeClass = cp.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            return ClassSignatureComparer.isPoolClassDifferent(classBeingRedefined, cp);
        } catch (Exception e) {
            LOGGER.error("Error analyzing class {} for reload necessity. Defaulting to yes.", e,
                    classBeingRedefined.getName());
        } finally {
            if (makeClass != null)
                makeClass.detach();
        }
        return true;
    }

    protected boolean isSyntheticClass(Class<?> classBeingRedefined) {
        return classBeingRedefined.getSimpleName().contains("$$_javassist")
                || classBeingRedefined.getName().startsWith("com.sun.proxy.$Proxy")
                || classBeingRedefined.getSimpleName().contains("$$Enhancer");
    }

}
