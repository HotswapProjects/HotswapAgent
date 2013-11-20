package org.hotswap.agent.util.classloader;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.scanner.ClassPathScanner;
import org.hotswap.agent.util.scanner.Scanner;
import org.hotswap.agent.util.scanner.ScannerVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.security.ProtectionDomain;

/**
 * Classloader patch which will redefine each patch via Javassist in the target classloader.
 * <p/>
 * Note that the class will typically be already accessible by parent classloader, but if it
 * is loaded from parent classloader, it does not have access to other child classloader classes.
 * <p/>
 * Redefine will work only if the class was not loaded by the child classloader. This may not be used
 * for Plugin class itself, because some target library classes may be enhanced by plugin reference
 * (e.g. to set some initialized property). Although the class resides in parent classloader it cannot
 * be redefined in child classloader with other definition - the classloader already knows about this class.
 * This is the reason, why plugin class cannot be executed in child classloader.
 *
 * @author Jiri Bubnik
 */
public class ClassLoaderDefineClassPatcher implements ClassLoaderPatcher {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassLoaderDefineClassPatcher.class);

    @Override
    public void patch(final ClassLoader classLoader, final ProtectionDomain protectionDomain) {
        ClassLoader agentClassLoader = getClass().getClassLoader();

        final ClassPool cp = new ClassPool();
        cp.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));

        Scanner scanner = new ClassPathScanner();

        try {
            scanner.scan("org/hotswap/agent/plugin", new ScannerVisitor() {
                @Override
                public void visit(InputStream file) throws IOException {
                    try {
                        CtClass patchClass = cp.makeClass(file);
                        try {
                            patchClass.toClass(classLoader, protectionDomain);
                        } catch (CannotCompileException e) {
                            LOGGER.trace("Skipping class definition in {} in app classloader {} - " +
                                    "class is probably already defined. This will happen " +
                                    "typically for the plugin class itself.", e,
                                    patchClass.getName(), classLoader);
                        }
                    } catch (Throwable e) {
                        LOGGER.trace("Skipping class definition app classloader {} - " +
                                "unknown error.", e, classLoader);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.error("Exception while scanning 'org/hotswap/agent/plugin'", e);
        }

        LOGGER.debug("Classloader {} patched with plugin classes from agent classloader {}.", classLoader, agentClassLoader);

    }

    @Override
    public boolean isPatchAvailable(ClassLoader classLoader) {
        // we can define class in any class loader
        // exclude synthetic classloader where it does not make any sense

        // sun.reflect.DelegatingClassLoader - created automatically by JVM to optimize reflection calls
        return !classLoader.getClass().getName().equals("sun.reflect.DelegatingClassLoader");
    }
}
