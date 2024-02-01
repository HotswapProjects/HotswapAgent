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
package org.hotswap.agent.annotation.handler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AppClassLoaderExecutor;
import org.hotswap.agent.util.HaClassFileTransformer;
import org.hotswap.agent.versions.DeploymentInfo;

public class PluginClassFileTransformer implements HaClassFileTransformer {
    protected static AgentLogger LOGGER = AgentLogger.getLogger(PluginClassFileTransformer.class);


    private final OnClassLoadEvent onClassLoadAnnotation;

    private final PluginAnnotation<OnClassLoadEvent> pluginAnnotation;

    private final List<LoadEvent> events;

    private final PluginManager pluginManager;

    public PluginClassFileTransformer(PluginManager pluginManager, PluginAnnotation<OnClassLoadEvent> pluginAnnotation) {
        this.pluginManager = pluginManager;
        this.pluginAnnotation = pluginAnnotation;
        this.onClassLoadAnnotation = pluginAnnotation.getAnnotation();
        this.events = Arrays.asList(onClassLoadAnnotation.events());
    }

    @Override
    public boolean isForRedefinitionOnly() {
        return !events.contains(LoadEvent.DEFINE);
    }

    public boolean isPluginDisabled(ClassLoader loader){
        if(loader != null && pluginManager != null && pluginManager.getPluginConfiguration(loader) != null) {
            return pluginManager.getPluginConfiguration(loader).isDisabledPlugin(pluginAnnotation.getPluginClass());
        }
        // can't tell
        return false;
    }

    public boolean shouldCheckVersion(){
        return pluginAnnotation.shouldCheckVersion();
    }

    public boolean isFallbackPlugin(){
        return pluginAnnotation.isFallBack();
    }

    public String getPluginGroup() {
        return pluginAnnotation.getGroup();
    }

    public boolean versionMatches(ClassLoader loader){
        if (pluginAnnotation.shouldCheckVersion()) {
            DeploymentInfo info = DeploymentInfo.fromClassLoader(loader);
            if (!pluginAnnotation.matches(info)) {
                LOGGER.debug("SKIPPING METHOD: {}, Deployment info: {}\n did not match with {}\n or {}", pluginAnnotation.method, info, pluginAnnotation.methodMatcher, pluginAnnotation.pluginMatcher);
                return false;
            }
        }
        return true;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if ((classBeingRedefined == null) ? !events.contains(LoadEvent.DEFINE) : !events.contains(LoadEvent.REDEFINE)) {
            LOGGER.trace("Not a handled event!", events);
            return classfileBuffer;
        }

        // check disabled plugins
        // noinspection unchecked
        if (pluginManager.getPluginConfiguration(loader).isDisabledPlugin(pluginAnnotation.getPluginClass())) {
            LOGGER.trace("Plugin NOT enabled! {}", pluginAnnotation);
            return classfileBuffer;
        }

        return transform(pluginManager, pluginAnnotation, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }


    @Override
    public String toString() {
        return "\n\t\t\tPluginClassFileTransformer [pluginAnnotation=" + pluginAnnotation + "]";
    }

    /**
     * Creats javaassist CtClass for bytecode manipulation. Add default
     * classloader.
     *
     * @param bytes new class definition
     * @param classLoader loader
     * @return created class
     * @throws NotFoundException
     */
    private static CtClass createCtClass(byte[] bytes, ClassLoader classLoader) throws IOException {
        ClassPool cp = new ClassPool();
        cp.appendSystemPath();
        cp.appendClassPath(new LoaderClassPath(classLoader));

        return cp.makeClass(new ByteArrayInputStream(bytes));
    }

    /**
     * Skip proxy and javassist synthetic classes.
     */
    protected static boolean isSyntheticClass(String className) {
        return className.contains("$$_javassist")
                || className.contains("$$_jvst")
                || className.startsWith("com/sun/proxy")
                || (className.startsWith("jdk/proxy") && className.contains("$Proxy"))
                ;
    }

    /**
     * Transformation callback as registered in initMethod:
     * hotswapTransformer.registerTransformer(). Resolve method parameters to
     * actual values, provide convenience parameters of javassist to streamline
     * the transformation.
     */
    private static byte[] transform(PluginManager pluginManager, PluginAnnotation<OnClassLoadEvent> pluginAnnotation, ClassLoader classLoader, String className, Class<?> redefiningClass, ProtectionDomain protectionDomain, byte[] bytes) {
        LOGGER.trace("Transforming.... '{}' using: '{}'", className, pluginAnnotation);
        // skip synthetic classes
        if (pluginAnnotation.getAnnotation().skipSynthetic()) {
            if (isSyntheticClass(className) || (redefiningClass != null && redefiningClass.isSynthetic())) {
                return bytes;
            }
        }

        // skip anonymous class
        if (pluginAnnotation.getAnnotation().skipAnonymous()) {
            if (className.matches("\\$\\d+$")) {
                return bytes;
            }
        }

        // ensure classloader initiated
        if (classLoader != null) {
            pluginManager.initClassLoader(classLoader, protectionDomain);
        }

        // default result
        byte[] result = bytes;

        // we may need to crate CtClass on behalf of the client and close it
        // after invocation.
        CtClass ctClass = null;

        List<Object> args = new ArrayList<>();
        for (Class<?> type : pluginAnnotation.getMethod().getParameterTypes()) {
            if (type.isAssignableFrom(ClassLoader.class)) {
                args.add(classLoader);
            } else if (type.isAssignableFrom(String.class)) {
                args.add(className);
            } else if (type.isAssignableFrom(Class.class)) {
                args.add(redefiningClass);
            } else if (type.isAssignableFrom(ProtectionDomain.class)) {
                args.add(protectionDomain);
            } else if (type.isAssignableFrom(byte[].class)) {
                args.add(bytes);
            } else if (type.isAssignableFrom(ClassPool.class)) {
                ClassPool classPool = new ClassPool();
                classPool.appendSystemPath();
                LOGGER.trace("Adding loader classpath " + classLoader);
                classPool.appendClassPath(new LoaderClassPath(classLoader));
                args.add(classPool);
            } else if (type.isAssignableFrom(CtClass.class)) {
                try {
                    ctClass = createCtClass(bytes, classLoader);
                    args.add(ctClass);
                } catch (IOException e) {
                    LOGGER.error("Unable create CtClass for '" + className + "'.", e);
                    return result;
                }
            } else if (type.isAssignableFrom(LoadEvent.class)) {
                args.add(redefiningClass == null ? LoadEvent.DEFINE : LoadEvent.REDEFINE);
            } else if (type.isAssignableFrom(AppClassLoaderExecutor.class)) {
                args.add(new AppClassLoaderExecutor(classLoader, protectionDomain));
            } else {
                LOGGER.error("Unable to call init method on plugin '" + pluginAnnotation.getPluginClass() + "'." + " Method parameter type '" + type + "' is not recognized for @Init annotation.");
                return result;
            }
        }
        try {
            // call method on plugin (or if plugin null -> static method)
            Object resultObject = pluginAnnotation.getMethod().invoke(pluginAnnotation.getPlugin(), args.toArray());

            if (resultObject == null) {
                // Ok, nothing has changed
            } else if (resultObject instanceof byte[]) {
                result = (byte[]) resultObject;
            } else if (resultObject instanceof CtClass) {
                result = ((CtClass) resultObject).toBytecode();

                // detach on behalf of the clinet - only if this is another
                // instance than we created (it is closed elsewhere)
                if (resultObject != ctClass) {
                    ((CtClass) resultObject).detach();
                }
            } else {
                LOGGER.error("Unknown result of @OnClassLoadEvent method '" + result.getClass().getName() + "'.");
            }

            // close CtClass if created from here
            if (ctClass != null) {
                // if result not set from the method, use class
                if (resultObject == null) {
                    result = ctClass.toBytecode();
                }
                ctClass.detach();
            }

        } catch (IllegalAccessException e) {
            LOGGER.error("IllegalAccessException in transform method on plugin '{}' class '{}' of classLoader '{}'",
                e, pluginAnnotation.getPluginClass(), className,
                classLoader != null ? classLoader.getClass().getName() : "");
        } catch (InvocationTargetException e) {
            LOGGER.error("InvocationTargetException in transform method on plugin '{}' class '{}' of classLoader '{}'",
                e, pluginAnnotation.getPluginClass(), className,
                classLoader != null ? classLoader.getClass().getName() : "");
        } catch (CannotCompileException e) {
            LOGGER.error("Cannot compile class after manipulation on plugin '{}' class '{}' of classLoader '{}'",
                e, pluginAnnotation.getPluginClass(), className,
                classLoader != null ? classLoader.getClass().getName() : "");
        } catch (IOException e) {
            LOGGER.error("IOException in transform method on plugin '{}' class '{}' of classLoader '{}'",
                e, pluginAnnotation.getPluginClass(), className,
                classLoader != null ? classLoader.getClass().getName() : "");
        }

        return result;
    }

}
