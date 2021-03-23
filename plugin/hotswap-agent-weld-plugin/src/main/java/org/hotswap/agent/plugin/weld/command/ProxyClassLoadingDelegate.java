/*
 * Copyright 2012, Stuart Douglas, and individual contributors as indicated
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.hotswap.agent.plugin.weld.command;

import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.weld.serialization.spi.ProxyServices;

/**
 * The Weld proxyFactory has its class loading tasks delegated to this class, which can then have some magic applied
 * to make weld think that the class has not been loaded yet.
 *
 * @author Stuart Douglas
 * @author Vladimir Dvorak
 */
public class ProxyClassLoadingDelegate {

    private static final ThreadLocal<Boolean> MAGIC_IN_PROGRESS = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public static final void beginProxyRegeneration() {
        MAGIC_IN_PROGRESS.set(true);
    }

    public static final void endProxyRegeneration() {
        MAGIC_IN_PROGRESS.remove();
    }

    public static Class<?> loadClass(final ClassLoader classLoader, final String className) throws ClassNotFoundException {
        if (MAGIC_IN_PROGRESS.get()) {
            throw new ClassNotFoundException("HotswapAgent");
        }
        return classLoader.loadClass(className);
    }

    public static Class<?> toClassWeld2(ClassFile ct, ClassLoader loader, ProtectionDomain domain) throws ClassNotFoundException {
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                final Class<?> originalProxyClass = loader.loadClass(ct.getName());
                try {
                    Map<Class<?>, byte[]> reloadMap = new HashMap<>();
                    reloadMap.put(originalProxyClass, ct.toBytecode());
                    // TODO : is this standard way how to reload class?
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
            }
        }
        Class<?> classFileUtilsClass = Class.forName("org.jboss.weld.util.bytecode.ClassFileUtils", true, loader);
        return (Class<?>) ReflectionHelper.invoke(null, classFileUtilsClass, "toClass",
                new Class[] { ClassFile.class, ClassLoader.class, ProtectionDomain.class }, ct, loader, domain);
    }

    public static Class<?> toClassWeld3(Object proxyFactory, ClassFile ct, Class<?> originalClass, ProxyServices proxyServices, ProtectionDomain domain) {
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                ClassLoader loader = originalClass.getClassLoader();
                if (loader == null) {
                    loader = Thread.currentThread().getContextClassLoader();
                }
                final Class<?> originalProxyClass = loader.loadClass(ct.getName());
                try {
                    Map<Class<?>, byte[]> reloadMap = new HashMap<>();
                    reloadMap.put(originalProxyClass, ct.toBytecode());
                    // TODO : is this standard way how to reload class?
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
            }
        }
        return (Class<?>) ReflectionHelper.invoke(proxyFactory, proxyFactory.getClass(), "$$ha$toClass",
                new Class[] { ClassFile.class, Class.class, ProxyServices.class, ProtectionDomain.class }, ct, originalClass, proxyServices, domain);
    }

}
