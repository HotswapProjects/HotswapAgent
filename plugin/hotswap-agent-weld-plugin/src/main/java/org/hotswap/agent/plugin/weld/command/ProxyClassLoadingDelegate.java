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
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.util.ByteArrayDataOutputStream;
import org.jboss.weld.util.bytecode.ClassFileUtils;

/**
 * The CDI proxyFactory has its class loading tasks delegated to this class, which can then have some magic applied
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

    public static Class<?> toClass(ClassFile ct, ClassLoader loader, ProtectionDomain domain) {
        if (MAGIC_IN_PROGRESS.get()) {
            try {
                final Class<?> originalProxyClass = loader.loadClass(ct.getName());
                try {
                    ByteArrayDataOutputStream out = new ByteArrayDataOutputStream();
                    ct.write(out);
                    Map<Class<?>, byte[]> reloadMap = new HashMap<Class<?>, byte[]>();
                    reloadMap.put(originalProxyClass, out.getBytes());
                    // TODO : is this standard way how to reload class?
                    PluginManager.getInstance().hotswap(reloadMap);
                    return originalProxyClass;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
                //it has not actually been loaded yet
                return ClassFileUtils.toClass(ct, loader, domain);
            }
        }
        return ClassFileUtils.toClass(ct, loader, domain);
    }

}
