/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.jvm;

import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassMap;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HotswapTransformer;
import org.hotswap.agent.util.HaClassFileTransformer;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;

/**
 * Class names MyClass$1, MyClass$2 are created in the order as anonymous class appears in the source code.
 * After anonymous class insertion/deletion the indexes are shifted producing not compatible hot swap.
 * <p/>
 * This patch will create class state info before the change (from current ClassLoader via reflection) and
 * after the change (from filesystem using javassist) find all compatible transitions.
 * <p/>
 * <p/>
 * For example if you exchange order the anonymous class appears in the source code, Transition may
 * produce something like:<ul>
 * <li>MyClass$1 -> MyClass$2</li>
 * <li>MyClass$2 -> MyClass$3</li>
 * <li>MyClass$3 -> MyClass$1</li>
 * </ul>
 * Then the transformation will behave:<ul>
 * <li>When the class MyClass$1 is hot swapped, the bytecode from MyClass$2 is returned (and renamed to MyClass$1)</li>
 * <li>When the class MyClass$2 is hot swapped, the bytecode from MyClass$3 is returned (and renamed to MyClass$2)</li>
 * <li>When the class MyClass$3 is hot swapped, the bytecode from MyClass$1 is returned (and renamed to MyClass$3)</li>
 * <li>When the class MyClass is hot swapped, all occurences of MyClass$1 are exchanged for MyClass$3</li>
 * <li>                         , all occurences of MyClass$2 are exchanged for MyClass$1</li>
 * <li>                         , all occurences of MyClass$3 are exchanged for MyClass$2</li>
 * </ul>
 * <p/>
 * Swap may produce even to not compatible change. Consider existing MyClass$1 and MyClass$2, then MyClass$1
 * is removed. Then hotswap is called only on MyClass$1, which contains different class to MyClass$2. Then
 * MyClass$1 is on hotswap replaced with empty implementation and new class MyClass$1000x is created to
 * contain code from the new MyClass$1 (class compatible with old MyClass$2). Not that because this is not
 * a true hotswap, old existing instances of MyClass$1 are updated to an empty class, not the new one.
 * When calling a method on this class, AbstractErrorMethod is thrown (this should be replaced to some
 * more clear error in the future).
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "AnonymousClassPatch",
        description = "Swap anonymous inner class names to avoid not compatible changes.",
        testedVersions = {"DCEVM"})
public class AnonymousClassPatchPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AnonymousClassPatchPlugin.class);

    @Init
    static HotswapTransformer hotswapTransformer;

    // Map ClassLoader -> (className -> infos about inner/local anonymous classes)
    // This caches information for one hotswap on main class and all anonymous classes
    private static Map<ClassLoader, Map<String, AnonymousClassInfos>> anonymousClassInfosMap =
            new WeakHashMap<ClassLoader, Map<String, AnonymousClassInfos>>();

    /**
     * Replace an anonymous class with an compatible change (from another class according to state info).
     * If no compatible class exists, replace with compatible empty implementation.
     */
    @OnClassLoadEvent(classNameRegexp = ".*\\$\\d+", events = LoadEvent.REDEFINE)
    public static CtClass patchAnonymousClass(ClassLoader classLoader, ClassPool classPool, String className, Class original)
            throws IOException, NotFoundException, CannotCompileException {

        String javaClass = className.replaceAll("/", ".");
        String mainClass = javaClass.replaceAll("\\$\\d+$", "");

        // skip synthetic classes
        if (classPool.find(className) == null)
            return null;

        AnonymousClassInfos info = getStateInfo(classLoader, classPool, mainClass);

        String compatibleName = info.getCompatibleTransition(javaClass);

        if (compatibleName != null) {
            LOGGER.debug("Anonymous class '{}' - replacing with class file {}.", javaClass, compatibleName);
            CtClass ctClass = classPool.get(compatibleName);
            ctClass.replaceClassName(compatibleName, javaClass);
            return ctClass;
        } else {
            LOGGER.debug("Anonymous class '{}' - not compatible change is replaced with empty implementation.", javaClass, compatibleName);

            // replace current class with empty implementation (to avid not compatible exception)
            CtClass ctClass = classPool.makeClass(javaClass);
            // replace superclass
            ctClass.setSuperclass(classPool.get(original.getSuperclass().getName()));
            // replace interfaces
            Class[] originalInterfaces = original.getInterfaces();
            CtClass[] interfaces = new CtClass[originalInterfaces.length];
            for (int i = 0; i < originalInterfaces.length; i++)
                interfaces[i] = classPool.get(originalInterfaces[i].getName());
            ctClass.setInterfaces(interfaces);

            return ctClass;

            // TODO provide implementation that will throw an exception
            //   throw new IllegalAccessError("HOTSWAP AGENT - obsolete anonymous class. This class has been
            // replaced with a new version. Automatic update of old instances containing references to obsolete
            // class is not supported yet.");
        }
    }

    private static boolean isHotswapAgentSyntheticClass(String compatibleName) {
        String anonymousClassIndexString = compatibleName.replaceAll("^.*\\$(\\d+)$", "$1");
        try {
            long anonymousClassIndex = Long.valueOf(anonymousClassIndexString);
            return anonymousClassIndex >= AnonymousClassInfos.UNIQUE_CLASS_START_INDEX;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(compatibleName + " is not in a format of className$i");
        }
    }

    // new anonymous class, not covered by hotswap (patchAnonymousClass) - register custom transformer and
    // on event swap and unregister.
    private static void registerReplaceOnLoad(final String newName, final CtClass anonymous) {
        hotswapTransformer.registerTransformer(null, newName, new HaClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                LOGGER.trace("Anonymous class '{}' - replaced.", newName);
                hotswapTransformer.removeTransformer(newName, this);
                try {
                    return anonymous.toBytecode();
                } catch (Exception e) {
                    LOGGER.error("Unable to create bytecode of class {}.", e, anonymous.getName());
                    return null;
                }
            }
            @Override
            public boolean isForRedefinitionOnly() {
                return false;
            }
        });
    }

    /**
     * If class contains anonymous classes, rename class references to compatible transition classes.
     * <p/>
     * If the transitioned class is not loaded by hotswap replace, catch class define event to do
     * the replacement.
     * <p/>
     * Define new synthetic classes for not compatible changes.
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public static byte[] patchMainClass(String className, ClassPool classPool, CtClass ctClass,
                                        ClassLoader classLoader, ProtectionDomain protectionDomain) throws IOException, CannotCompileException, NotFoundException {
        String javaClassName = className.replaceAll("/", ".");

        // check if has anonymous classes
        if (!ClassLoaderHelper.isClassLoaded(classLoader, javaClassName + "$1"))
            return null;


        AnonymousClassInfos stateInfo = getStateInfo(classLoader, classPool, javaClassName);
        Map<AnonymousClassInfo, AnonymousClassInfo> transitions = stateInfo.getCompatibleTransitions();

        ClassMap replaceClassNameMap = new ClassMap();
        for (Map.Entry<AnonymousClassInfo, AnonymousClassInfo> entry : transitions.entrySet()) {
            String compatibleName = entry.getKey().getClassName();
            String newName = entry.getValue().getClassName();

            if (!newName.equals(compatibleName)) {
                replaceClassNameMap.put(newName, compatibleName);
                LOGGER.trace("Class '{}' replacing '{}' for '{}'", javaClassName, newName, compatibleName);
            }

            // new class (not known by current classloader)
            if (isHotswapAgentSyntheticClass(compatibleName)) {
                LOGGER.debug("Anonymous class '{}' not comatible and is replaced with synthetic class '{}'", newName, compatibleName);
                // define contens of new class as new unique "myClass$hotswapAgentXx" class
                CtClass anonymous = classPool.get(newName);
                anonymous.replaceClassName(newName, compatibleName);
                anonymous.toClass(classLoader, protectionDomain);
            } else if (!ClassLoaderHelper.isClassLoaded(classLoader, newName)) {
                CtClass anonymous = classPool.get(compatibleName);
                anonymous.replaceClassName(compatibleName, newName);

                // is a new class of standard type myClass$x -> replace on load
                LOGGER.debug("Anonymous class '{}' - will be replaced from class file {}.", newName, compatibleName);
                registerReplaceOnLoad(newName, anonymous);
            }
        }

        // rename all class names according to the map

        // TODO: it could be done via classPool but it doesn't work
//        CtClass ctClass = classPool.get(javaClassName);
        ctClass.replaceClassName(replaceClassNameMap);

        LOGGER.reload("Class '{}' has been enhanced with anonymous classes for hotswap.", className);
        return ctClass.toBytecode();
    }

    /**
     * Calculate anonymous class new/previous state info from current classloader/filesystem.
     * It checks, if state info is current via modification date on the main class file.
     * <p/>
     * <p/>Note: Synchronized may be too restrictive, in case of performance issues consider synchronization
     * only on a classloader and class.
     */
    private static synchronized AnonymousClassInfos getStateInfo(ClassLoader classLoader, ClassPool classPool, String className) {
        Map<String, AnonymousClassInfos> classInfosMap = getClassInfosMapForClassLoader(classLoader);

        AnonymousClassInfos infos = classInfosMap.get(className);

        if (infos == null || !infos.isCurrent(classPool)) {
            if (infos == null)
                LOGGER.trace("Creating new infos for className {}", className);
            else
                LOGGER.trace("Creating new infos, current is obsolete for className {}", className);

            infos = new AnonymousClassInfos(classPool, className);
            infos.mapPreviousState(new AnonymousClassInfos(classLoader, className));
            classInfosMap.put(className, infos);
        } else {
            LOGGER.trace("Returning existing infos for className {}", className);
        }
        return infos;
    }

    /**
     * Return classInfos for a classloader. Hold known classloaders in weak hash map.
     */
    private static Map<String, AnonymousClassInfos> getClassInfosMapForClassLoader(ClassLoader classLoader) {
        Map<String, AnonymousClassInfos> classInfosMap = anonymousClassInfosMap.get(classLoader);
        if (classInfosMap == null) {
            synchronized (classLoader) {
                classInfosMap = anonymousClassInfosMap.get(classLoader);
                if (classInfosMap == null) {
                    classInfosMap = new HashMap<>();
                    anonymousClassInfosMap.put(classLoader, classInfosMap);
                }
            }
        }
        return classInfosMap;
    }
}
