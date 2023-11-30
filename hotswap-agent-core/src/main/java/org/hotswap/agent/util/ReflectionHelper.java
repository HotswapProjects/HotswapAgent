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
package org.hotswap.agent.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.hotswap.agent.logging.AgentLogger;

/**
 * Convenience methods on java reflection API.
 */
public class ReflectionHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ReflectionHelper.class);

    /**
     * Convenience wrapper to reflection method invoke API. Invoke the method and hide checked exceptions.
     *
     * @param target         object to invoke the method on (or null for static methods)
     * @param clazz          class name
     * @param methodName     method name
     * @param parameterTypes parameter types to resolve method name
     * @param args           actual arguments
     * @return invocation result or null
     * @throws IllegalArgumentException if method not found
     * @throws IllegalStateException    for InvocationTargetException (exception in invoked method)
     */
    public static Object invoke(Object target, Class<?> clazz, String methodName, Class<?>[] parameterTypes,
            Object... args) {
        try {
            Method method = null;
            try {
                method = clazz.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                method = clazz.getDeclaredMethod(methodName, parameterTypes);
            }
            method.setAccessible(true);

            return method.invoke(target, args);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Illegal arguments method %s.%s(%s) on %s, params %s", clazz.getName(), methodName,
                    Arrays.toString(parameterTypes), target, Arrays.toString(args)), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(String.format("Error invoking method %s.%s(%s) on %s, params %s", clazz.getName(), methodName,
                    Arrays.toString(parameterTypes), target, Arrays.toString(args)), e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("No such method %s.%s(%s) on %s, params %s", clazz.getName(), methodName,
                    Arrays.toString(parameterTypes), target, Arrays.toString(args)), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("No such method %s.%s(%s) on %s, params %s", clazz.getName(), methodName,
                    Arrays.toString(parameterTypes), target, Arrays.toString(args)), e);
        }
    }

    /**
     * Convenience wrapper to reflection method invoke API. Invoke the method and
     * swallow exceptions due to missing methods. Use this method if you have
     * multiple framework support and the method may not exist in current version.
     *
     * @param target         object to invoke the method on (or null for static
     *                       methods)
     * @param clazz          className name
     * @param cl             Classloader to load the class
     * @param methodName     method name
     * @param parameterTypes parameter types to resolve method name
     * @param args           actual arguments
     * @return invocation result or null
     * @throws IllegalStateException for InvocationTargetException (exception in
     *                               invoked method)
     */
    public static Object invokeNoException(Object target, String className, ClassLoader cl, String methodName,
            Class<?>[] parameterTypes, Object... args) {
        Class<?> clazz;
        try {
            clazz = cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            LOGGER.trace("Class {} not found", e, className);
            return null;
        }

        try {
            return invoke(target, clazz, methodName, parameterTypes, args);
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Method {}.{} not found", e, className, methodName);
            return null;
        }
    }

    /**
     * Convenience wrapper to reflection method invoke API. Invoke the method and
     * hide checked exceptions.
     *
     * @param target     object to invoke the method on (or null for static methods)
     * @param methodName method name
     * @return invocation result or null
     * @throws IllegalArgumentException if method not found
     * @throws IllegalStateException    for InvocationTargetException (exception in
     *                                  invoked method)
     */
    public static Object invoke(Object target, String methodName) {
        return invoke(target, target.getClass(), methodName, new Class[] {});
    }

    public static Object invokeConstructor(String className, ClassLoader cl, Class<?>[] parameterTypes,
                                           Object... args) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName(className, true, cl);
        Constructor constructor = clazz.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    /**
     * Convenience wrapper to reflection field access API. Get field value and hide
     * checked exceptions. Field class is set by
     *
     * @param target    object to get field value (or null for static methods)
     * @param fieldName field name
     * @return field value
     * @throws IllegalArgumentException if field not found
     */
    public static Object get(Object target, String fieldName) {
        if (target == null)
            throw new NullPointerException("Target object cannot be null.");

        Class<?> clazz = target.getClass();

        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                break;
            } catch (NoSuchFieldException e) {
                // ignore
            }
            clazz = clazz.getSuperclass();
        }

        if (clazz == null) {
            throw new IllegalArgumentException(String.format("No such field %s.%s on %s", target.getClass(), fieldName, target));
        }

        return get(target, clazz, fieldName);
    }

    /**
     * Convenience wrapper to reflection field access API. Get field value and hide
     * checked exceptions.
     *
     * @param target    object to get field value (or null for static methods)
     * @param clazz     class name
     * @param fieldName field name
     * @return field value
     * @throws IllegalArgumentException if field not found
     */
    public static Object get(Object target, Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("No such field %s.%s on %s", clazz.getName(), fieldName, target), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Illegal access field %s.%s on %s", clazz.getName(), fieldName, target), e);
        }
    }

    /**
     * Convenience wrapper to reflection field access API. Get field value and
     * swallow exceptions. Use this method if you have multiple framework support
     * and the field may not exist in current version.
     *
     * @param target    object to get field value (or null for static methods)
     * @param clazz     class name
     * @param fieldName field name
     * @return field value or null if an exception
     */
    public static Object getNoException(Object target, Class<?> clazz, String fieldName) {
        try {
            return get(target, clazz, fieldName);
        } catch (Exception e) {
            LOGGER.trace("Error getting field {}.{} on object {}", e, clazz, fieldName, target);
            return null;
        }
    }

    /**
     * Convenience wrapper to reflection field access API. Get field value and
     * swallow exceptions. Use this method if you have multiple framework support
     * and the field may not exist in current version.
     *
     * @param target    object to get field value (or null for static methods)
     * @param className class name
     * @param cl        class loader to load the target class
     * @param fieldName field name
     * @return field value or null if an exception
     */
    public static Object getNoException(Object target, String className, ClassLoader cl, String fieldName) {
        Class<?> clazz;
        try {
            clazz = cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            LOGGER.trace("Class {} not found", e, className);
            return null;
        }

        return getNoException(target, clazz, fieldName);
    }

    /**
     * Convenience wrapper to reflection field access API. Set field value and hide
     * checked exceptions.
     *
     * @param target    object to get field value (or null for static methods)
     * @param clazz     class name
     * @param fieldName field name
     * @param value     field value
     * @throws IllegalArgumentException if field not found
     */
    public static void set(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("No such field %s.%s on %s", clazz.getName(), fieldName, target), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Illegal access field %s.%s on %s", clazz.getName(), fieldName, target), e);
        }
    }

    public static void set(Object target, String fieldName, Object value) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                break;
            } catch (NoSuchFieldException e) {
                // ignore
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(String.format("Illegal access field %s.%s on %s", clazz.getName(),
                        fieldName, target), e);
            }
            clazz = clazz.getSuperclass();
        }

        if (clazz == null) {
            throw new IllegalArgumentException(String.format("No such field %s.%s on %s", target.getClass(), fieldName, target));
        }
    }
}
