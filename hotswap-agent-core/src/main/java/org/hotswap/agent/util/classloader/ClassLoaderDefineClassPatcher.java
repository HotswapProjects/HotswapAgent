package org.hotswap.agent.util.classloader;

import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.*;
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
    public void patch(final ClassLoader classLoaderFrom, final String pluginPath,
                      final ClassLoader classLoaderTo, final ProtectionDomain protectionDomain) {

        final ClassPool cp = new ClassPool();
        cp.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));

        Scanner scanner = new ClassPathScanner();

        try {
            scanner.scan(classLoaderFrom, pluginPath, new ScannerVisitor() {
                @Override
                public void visit(InputStream file) throws IOException {
                    try {
                        CtClass patchClass = cp.makeClass(file);

                        // skip plugin classes
                        // TODO this should be skipped only in patching application classloader. To copy
                         // classes into agent classloader, Plugin class must be copied as well
//                        if (patchClass.hasAnnotation(Plugin.class)) {
//                            LOGGER.trace("Skipping plugin class: " + patchClass.getName());
//                            return;
//                        }

                        try {
                            // force to load class in classLoaderFrom (it may not yet be loaded) and if the classLoaderTo
                            // is parent of classLoaderFrom, after definition in classLoaderTo will classLoaderFrom return
                            // class from parent classloader instead own definition (hence change of behaviour).
                            classLoaderFrom.loadClass(patchClass.getName());
                            // and load the class in classLoaderTo as well. NOw the class is defined in BOTH classloaders.
                            patchClass.toClass(classLoaderTo, protectionDomain);
                        } catch (CannotCompileException e) {
                            LOGGER.trace("Skipping class definition in {} in app classloader {} - " +
                                    "class is probably already defined.", patchClass.getName(), classLoaderTo);
                        }
                    } catch (Throwable e) {
                        LOGGER.trace("Skipping class definition app classloader {} - " +
                                "unknown error.", e, classLoaderTo);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.error("Exception while scanning 'org/hotswap/agent/plugin'", e);
        }

        LOGGER.debug("Classloader {} patched with plugin classes from agent classloader {}.", classLoaderTo, classLoaderFrom);

    }

    @Override
    public boolean isPatchAvailable(ClassLoader classLoader) {
        // we can define class in any class loader
        // exclude synthetic classloader where it does not make any sense

        // sun.reflect.DelegatingClassLoader - created automatically by JVM to optimize reflection calls
        return !classLoader.getClass().getName().equals("sun.reflect.DelegatingClassLoader");
    }
}
