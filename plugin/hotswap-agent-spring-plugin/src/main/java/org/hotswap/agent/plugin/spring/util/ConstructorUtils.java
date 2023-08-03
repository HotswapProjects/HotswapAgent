package org.hotswap.agent.plugin.spring.util;

import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * copy from org.springframework.beans.factory.support.ConstructorResolver#getCandidateMethods(java.lang.Class, org.springframework.beans.factory.support.RootBeanDefinition)
 * The method is not public, so we have to copy it. It avoids using the reflection invocation.
 */
public class ConstructorUtils {

    public static Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged((PrivilegedAction<Method[]>) () ->
                    (mbd.isNonPublicAccessAllowed() ?
                            ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods()));
        }
        else {
            return (mbd.isNonPublicAccessAllowed() ?
                    ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
        }
    }
}
