package org.hotswap.agent.annotation.handler;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AppClassLoaderExecutor;
import org.hotswap.agent.util.HotswapTransformer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Transform method handler - handle @OnClassLoadEvent annotation on a method.
 *
 * @author Jiri Bubnik
 */
public class OnClassLoadedHandler implements PluginHandler<OnClassLoadEvent> {
    private static AgentLogger LOGGER = AgentLogger.getLogger(OnClassLoadedHandler.class);

    protected PluginManager pluginManager;

    protected HotswapTransformer hotswapTransformer;

    public OnClassLoadedHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        this.hotswapTransformer = pluginManager.getHotswapTransformer();

        if (hotswapTransformer == null) {
            throw new IllegalArgumentException("Error instantiating OnClassLoadedHandler. Hotswap transformer is missing in PluginManager.");
        }
    }

    @Override
    public boolean initField(PluginAnnotation<OnClassLoadEvent> pluginAnnotation) {
        throw new IllegalAccessError("@OnClassLoadEvent annotation not allowed on fields.");
    }

    @Override
    public boolean initMethod(final PluginAnnotation<OnClassLoadEvent> pluginAnnotation) {
        LOGGER.debug("Init for method " + pluginAnnotation.getMethod());

        final OnClassLoadEvent annot = pluginAnnotation.getAnnotation();

        if (hotswapTransformer == null) {
            LOGGER.error("Error in init for method " + pluginAnnotation.getMethod() + ". Hotswap transformer is missing.");
            return false;
        }

        if (annot == null) {
            LOGGER.error("Error in init for method " + pluginAnnotation.getMethod() + ". Annotation missing.");
            return false;
        }

        ClassLoader appClassLoader = null;
        if (pluginAnnotation.getPlugin() != null)
            appClassLoader = pluginManager.getPluginRegistry().getAppClassLoader(pluginAnnotation.getPlugin());

        hotswapTransformer.registerTransformer(appClassLoader, annot.classNameRegexp(), new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                List<LoadEvent> events = Arrays.asList(annot.events());
                if ((classBeingRedefined == null) ? !events.contains(LoadEvent.DEFINE) : !events.contains(LoadEvent.REDEFINE)) {
                    // Hotswap reload which is the client not interested of
                    return classfileBuffer;
                }

                return OnClassLoadedHandler.this.transform(pluginAnnotation, loader, className,
                        classBeingRedefined, protectionDomain, classfileBuffer);
            }
        });

        return true;
    }

    /**
     * Transformation callback as registered in initMethod: hotswapTransformer.registerTransformer().
     * Resolve method parameters to actual values, provide convenience parameters of javassist to
     * streamline the transformation.
     */
    public byte[] transform(PluginAnnotation<OnClassLoadEvent> pluginAnnotation,
                            ClassLoader classLoader, String className,
                            Class<?> redefiningClass, ProtectionDomain protectionDomain, byte[] bytes) {
        // skip synthetic classes
        if (pluginAnnotation.getAnnotation().skipSynthetic()) {
            if (isSyntheticClass(className) || (redefiningClass != null && redefiningClass.isSynthetic()))
                return bytes;
        }

        // skip anonymous class
        if (pluginAnnotation.getAnnotation().skipAnonymous()) {
            if (className.matches("\\$\\d+$"))
                return bytes;
        }

        // ensure classloader initiated
        if (classLoader != null)
            pluginManager.initClassLoader(classLoader, protectionDomain);

        // check disabled plugins
        //noinspection unchecked
        if (pluginManager.getPluginConfiguration(classLoader).isDisabledPlugin(pluginAnnotation.getPluginClass()))
            return bytes;

        // default result
        byte[] result = bytes;

        // we may need to crate CtClass on behalf of the client and close it after invocation.
        CtClass ctClass = null;

        List<Object> args = new ArrayList<Object>();
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
                LOGGER.error("Unable to call init method on plugin '" + pluginAnnotation.getPluginClass() + "'." +
                        " Method parameter type '" + type + "' is not recognized for @Init annotation.");
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

                // detach on behalf of the clinet - only if this is another instance than we created (it is closed elsewhere)
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
            LOGGER.error("IllegalAccessException in transform method on plugin '" +
                    pluginAnnotation.getPluginClass() + "' class '" + className + "'.", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("InvocationTargetException in transform method on plugin '" +
                    pluginAnnotation.getPluginClass() + "' class '" + className + "'.", e);
        } catch (CannotCompileException e) {
            LOGGER.error("Cannot compile class after manipulation on plugin '" +
                    pluginAnnotation.getPluginClass() + "' class '" + className + "'.", e);
        } catch (IOException e) {
            LOGGER.error("IOException in transform method on plugin '" +
                    pluginAnnotation.getPluginClass() + "' class '" + className + "'.", e);
        }

        return result;
    }

    /**
     * Creats javaassist CtClass for bytecode manipulation. Add default classloader.
     *
     * @param bytes       new class definition
     * @param classLoader loader
     * @return created class
     * @throws NotFoundException
     */
    private CtClass createCtClass(byte[] bytes, ClassLoader classLoader) throws IOException {
        ClassPool cp = new ClassPool();
        cp.appendSystemPath();
        cp.appendClassPath(new LoaderClassPath(classLoader));

        return cp.makeClass(new ByteArrayInputStream(bytes));
    }


    /**
     * Skip proxy and javassist synthetic classes.
     */
    protected boolean isSyntheticClass(String className) {
        return className.contains("$$_javassist") || className.startsWith("com/sun/proxy");
    }

}
