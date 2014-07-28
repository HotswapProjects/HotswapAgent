package org.hotswap.agent.util;

import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Convenience methods on java reflection API.
 */
public class ReflectionHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ReflectionHelper.class);

    private static Map<String, Field> fieldCache = new WeakHashMap<String, Field>();
    private static Map<String, Method> methodCache = new WeakHashMap<String, Method>();

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
    public static Object invoke(Object target, Class<?> clazz, String methodName, Class[] parameterTypes, Object... args) {
        String key = clazz.getName() + "." + methodName + "(" + Arrays.toString(parameterTypes) + ")";
        try {
            Method method = methodCache.get(key);
            if (method == null) {
                try {
                    method = clazz.getMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException e) {
                    method = clazz.getDeclaredMethod(methodName, parameterTypes);
                }
                method.setAccessible(true);
                methodCache.put(key, method);
            }

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
     * Convenience wrapper to reflection method invoke API. Get field value and hide checked exceptions.
     *
     * @param target    object to get field value (or null for static methods)
     * @param clazz     class name
     * @param fieldName field name
     * @return field value
     * @throws IllegalArgumentException if field not found
     */
    public static Object get(Object target, Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "." + fieldName;
        try {
            Field field = fieldCache.get(key);
            if (field == null) {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                fieldCache.put(key, field);
            }

            return field.get(target);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("No such field %s.%s on %s", clazz.getName(), fieldName, target), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Illegal access field %s.%s on %s", clazz.getName(), fieldName, target), e);
        }
    }

    /**
     * Convenience wrapper to reflection method invoke API. Get field value and swallow exceptions.
     * Use this method if you have multiple framework support and the field may not exist in current version.
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
     * Convenience wrapper to reflection method invoke API. Set field value and hide checked exceptions.
     *
     * @param target    object to get field value (or null for static methods)
     * @param clazz     class name
     * @param fieldName field name
     * @param value field value
     * @throws IllegalArgumentException if field not found
     */
    public static void set(Object target, Class<?> clazz, String fieldName, Object value) {
        String key = clazz.getName() + "." + fieldName;
        try {
            Field field = fieldCache.get(key);
            if (field == null) {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                fieldCache.put(key, field);
            }

            field.set(target, value);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("No such field %s.%s on %s", clazz.getName(), fieldName, target), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Illegal access field %s.%s on %s", clazz.getName(), fieldName, target), e);
        }
    }
}
