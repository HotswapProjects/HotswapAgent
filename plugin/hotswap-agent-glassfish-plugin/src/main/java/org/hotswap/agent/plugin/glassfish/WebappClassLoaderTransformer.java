package org.hotswap.agent.plugin.glassfish;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class WebappClassLoaderTransformer {

    protected static AgentLogger LOGGER = AgentLogger.getLogger(WebappClassLoaderTransformer.class);

    private static boolean webappClassLoaderPatched = false;

    @OnClassLoadEvent(classNameRegexp = "org.glassfish.web.loader.WebappClassLoader")
    public static void patchWebappClassLoader(ClassPool classPool,CtClass ctClass) throws CannotCompileException, NotFoundException {
        if (!webappClassLoaderPatched) {
            try {
                // clear classloader cache
                ctClass.getDeclaredMethod("getResource", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                        "resourceEntries.clear();"
                );
                ctClass.getDeclaredMethod("getResourceAsStream", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                        "resourceEntries.clear();"
                );
                webappClassLoaderPatched = true;
            } catch (NotFoundException e) {
                LOGGER.trace("WebappClassLoader does not contain getResource(), getResourceAsStream method.");
            }
        }
    }

}
