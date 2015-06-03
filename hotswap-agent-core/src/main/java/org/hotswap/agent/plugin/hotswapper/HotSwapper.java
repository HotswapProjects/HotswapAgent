package org.hotswap.agent.plugin.hotswapper;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by bubnik on 22.5.2014.
 */
public class HotSwapper {

    /**
     * Swap class definition from another class file.
     * <p/>
     * This is mainly useful for unit testing - declare multiple version of a class and then
     * hotswap definition and do the tests.
     *
     * @param original original class currently in use
     * @param swap     fully qualified class name of class to swap
     * @throws Exception swap exception
     */
    public static void swapClasses(Class original, String swap) throws Exception {
        // need to recreate classpool on each swap to avoid stale class definition
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(original.getClassLoader()));

        CtClass ctClass = classPool.getAndRename(swap, original.getName());

        reload(original, ctClass.toBytecode());
    }

    private static void reload(Class original, byte[] bytes) {
        Map<Class<?>, byte[]> reloadMap = new HashMap<Class<?>, byte[]>();
        reloadMap.put(original, bytes);

        PluginManager.getInstance().hotswap(reloadMap);
    }

    public static Class newClass(String className, String directory, ClassLoader cl){
        try {
            ClassPool classPool = new ClassPool();
            classPool.appendClassPath(new LoaderClassPath(cl));
            CtClass makeClass = classPool.makeClass(className);
            makeClass.writeFile(directory);
            return makeClass.toClass();
        } catch (Throwable ex) {
            Logger.getLogger(HotSwapper.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } 
    }


}
