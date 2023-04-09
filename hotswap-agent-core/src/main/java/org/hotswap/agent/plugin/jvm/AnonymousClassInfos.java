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

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Info about anonymous classes.
 * <p/>
 * This class will on construction search for all anonymous classes of the main class and calculate
 * superclass, interfaces, all methods signature and all fields signature. Depending on used constructor
 * this is done via reflection from ClassLoader (current loaded state) or from ClassPool via javaassist.
 * Note that ClassPool uses LoadClassPath on the ClassLoader and hence are resources resolved via the
 * ClassLoader. Javaasist resolves the resource and returns bytcode as is in the resource file.
 * <p/>
 * Use mapPreviousState() to create compatible transition mapping between old state and new state. This mapping
 * is then used by plugin to swap class bytecoded to retain hotswap changes compatible
 *
 * @author Jiri Bubnik
 */
public class AnonymousClassInfos {
    private static AgentLogger LOGGER = AgentLogger.getLogger(AnonymousClassInfos.class);

    // start indexing hotswap created synthetic anonymous classes from this index to avoid collision with existing
    public static final int UNIQUE_CLASS_START_INDEX = 10000;

    // how many milliseconds is delta is considered as same time in modification check
    private static final long ALLOWED_MODIFICATION_DELTA = 100;

    // counter to create uniqueue class name
    static int uniqueClass = UNIQUE_CLASS_START_INDEX;

    // previous state
    AnonymousClassInfos previous;

    // calculated transitions
    Map<AnonymousClassInfo, AnonymousClassInfo> compatibleTransitions;

    // mo
    long lastModifiedTimestamp = 0;

    // the main class
    String className;

    // zero based index list of anonymous classes
    List<AnonymousClassInfo> anonymousClassInfoList = new ArrayList<>();

    /**
     * Create info of the current state from the classloader via reflection.
     *
     * @param classLoader classloader to use
     * @param className   main class
     */
    public AnonymousClassInfos(ClassLoader classLoader, String className) {
        this.className = className;

        try {
            // reflective call to check already loaded class (not to load a new one)
            Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[]{String.class});
            m.setAccessible(true);

            int i = 1;
            while (true) {

                Class anonymous = (Class) m.invoke(classLoader, className + "$" + i);
                if (anonymous == null)
                    break;

                anonymousClassInfoList.add(i - 1, new AnonymousClassInfo(anonymous));
                i++;
            }
        } catch (Exception e) {
            throw new Error("Unexpected error in checking loaded classes", e);
        }
    }

    /**
     * Create info of the new state from the classPool via javassist.
     *
     * @param classPool classPool to resolve class files
     * @param className main class
     */
    public AnonymousClassInfos(ClassPool classPool, String className) {
        this.className = className;
        lastModifiedTimestamp = lastModified(classPool, className);

        // search for declared classes in new state to skip obsolete anonymous inner classes on filesystem
        List<CtClass> declaredClasses;
        try {
            CtClass ctClass = classPool.get(className);
            declaredClasses = Arrays.asList(ctClass.getNestedClasses());
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Class " + className + " not found.");
        }


        int i = 1;
        while (true) {
            try {
                CtClass anonymous = classPool.get(className + "$" + i);
                if (!declaredClasses.contains(anonymous))
                    break; // skip obsolete classes
                anonymousClassInfoList.add(i - 1, new AnonymousClassInfo(anonymous));
                i++;
            } catch (NotFoundException e) {
                // up to first not found class
                break;
            } catch (Exception e) {
                throw new Error("Unable to create AnonymousClassInfo definition for class " + className + "$i", e);
            }
        }
        LOGGER.trace("Anonymous class '{}' scan finished with {} classes found", className, i - 1);
    }

    /**
     * Search for a mapping between previous and nwe anonymous classes.
     *
     * @return map previous -> new. If no mapping to previous exists, synthetic class name is created.
     */
    private void calculateCompatibleTransitions() {
        compatibleTransitions = new HashMap<>();

        // create a copy to remove resolved items
        List<AnonymousClassInfo> previousInfos = new ArrayList<>(previous.anonymousClassInfoList);
        List<AnonymousClassInfo> currentInfos = new ArrayList<>(anonymousClassInfoList);

        // previous classes are discarded and cannot be used
        if (previousInfos.size() > currentInfos.size()) {
            if (currentInfos.size() == 0)
                previousInfos.clear();
            else
                previousInfos = previousInfos.subList(0, currentInfos.size());
        }

        // try to match - exact, than signatures (enclosing method may change), than class signature
        searchForMappings(compatibleTransitions, previousInfos, currentInfos, new AnonymousClassInfoMatcher() {
            @Override
            public boolean match(AnonymousClassInfo previous, AnonymousClassInfo current) {
                return previous.matchExact(current);
            }
        });

        searchForMappings(compatibleTransitions, previousInfos, currentInfos, new AnonymousClassInfoMatcher() {
            @Override
            public boolean match(AnonymousClassInfo previous, AnonymousClassInfo current) {
                return previous.matchSignatures(current);
            }
        });

        searchForMappings(compatibleTransitions, previousInfos, currentInfos, new AnonymousClassInfoMatcher() {
            @Override
            public boolean match(AnonymousClassInfo previous, AnonymousClassInfo current) {
                return previous.matchClassSignature(current);
            }
        });


        // how many anonymous classes will be defined
        int newDefinitionCount = anonymousClassInfoList.size();
        // last myClass$index
        int lastAnonymousClassIndex = previous.anonymousClassInfoList.size();

        // not matched
        for (AnonymousClassInfo currentNotMatched : currentInfos) {
            if (lastAnonymousClassIndex < newDefinitionCount) {
                // free anonymous class available - this will be registered with onDefine event, +1 because one based index name
                compatibleTransitions.put(new AnonymousClassInfo(className + "$" + (lastAnonymousClassIndex + 1)), currentNotMatched);
                lastAnonymousClassIndex++;
            } else {
                compatibleTransitions.put(new AnonymousClassInfo(className + "$" + uniqueClass++), currentNotMatched);
            }
        }

        if (LOGGER.isLevelEnabled(AgentLogger.Level.TRACE)) {
            for (Map.Entry<AnonymousClassInfo, AnonymousClassInfo> mapping : compatibleTransitions.entrySet()) {
                LOGGER.trace("Transition {} => {}", mapping.getKey().getClassName(), mapping.getValue().getClassName());
            }
        }
    }

    /**
     * Iterate through both lists and find matching anonymous classes using matcher.
     * Found matches are removed from previous and current lists and added to transitions.
     */
    private void searchForMappings(Map<AnonymousClassInfo, AnonymousClassInfo> transitions, List<AnonymousClassInfo> previousInfos, List<AnonymousClassInfo> currentInfos,
                                   AnonymousClassInfoMatcher matcher) {
        for (ListIterator<AnonymousClassInfo> previousIt = previousInfos.listIterator(); previousIt.hasNext(); ) {
            AnonymousClassInfo previous = previousIt.next();

            for (ListIterator<AnonymousClassInfo> currentIt = currentInfos.listIterator(); currentIt.hasNext(); ) {
                AnonymousClassInfo current = currentIt.next();

                // found and resolved
                if (matcher.match(previous, current)) {
                    transitions.put(previous, current);
                    previousIt.remove();
                    currentIt.remove();
                    break;
                }
            }
        }
    }

    /**
     * Returns stored info of an anonymous class
     *
     * @param className class name of the anonymous class (Should be in the form of MyClass$3)
     * @return class info x null
     */
    public AnonymousClassInfo getAnonymousClassInfo(String className) {
        for (AnonymousClassInfo info : anonymousClassInfoList) {
            if (className.equals(info.getClassName())) {
                return info;
            }
        }
        return null;
    }

    /**
     * Set previous class info state and calculate compatible transitions.
     * Usually new state is created from classpool, while old state from classloader.
     *
     * @param previousAnonymousClassInfos previous state
     */
    public void mapPreviousState(AnonymousClassInfos previousAnonymousClassInfos) {
        this.previous = previousAnonymousClassInfos;

        // hold only one state back
        previousAnonymousClassInfos.previous = null;

        // calculate the mapping
        calculateCompatibleTransitions();
    }

    /**
     * Return true, if last modification timestamp is same as current timestamp of className.
     *
     * @param classPool classPool to check className file
     * @return true if is current
     */
    public boolean isCurrent(ClassPool classPool) {
        return lastModifiedTimestamp >= lastModified(classPool, className) - ALLOWED_MODIFICATION_DELTA;
    }

    // get timestamp on the main class file
    private long lastModified(ClassPool classPool, String className) {
        String file = classPool.find(className).getFile();
        return new File(file).lastModified();
    }

    // matcher helper
    private interface AnonymousClassInfoMatcher {
        public boolean match(AnonymousClassInfo previous, AnonymousClassInfo current);
    }

    /**
     * Returns calculated compatible transitions.
     *
     * @return calculated compatible transitions.
     */
    public Map<AnonymousClassInfo, AnonymousClassInfo> getCompatibleTransitions() {
        return compatibleTransitions;
    }

    /**
     * Find compatible transition class name.
     *
     * @param className name of existing class (old)
     * @return name of compatible new class that should replace old class. Can be null if no compatible class found.
     */
    public String getCompatibleTransition(String className) {
        for (Map.Entry<AnonymousClassInfo, AnonymousClassInfo> transition : compatibleTransitions.entrySet()) {
            if (transition.getKey().getClassName().equals(className))
                return transition.getValue().getClassName();
        }

        return null;
    }

}
