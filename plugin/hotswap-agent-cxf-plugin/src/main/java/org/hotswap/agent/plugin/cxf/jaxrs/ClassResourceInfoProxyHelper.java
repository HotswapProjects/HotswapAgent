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
package org.hotswap.agent.plugin.cxf.jaxrs;

import java.lang.reflect.Method;

import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.utils.ResourceUtils;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.util.proxy.MethodFilter;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.Proxy;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.javassist.util.proxy.ProxyObject;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * The Class ClassResourceInfoProxyHelper.
 */
public class ClassResourceInfoProxyHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassResourceInfoProxyHelper.class);

    private static Class<?> classResourceInfoProxyClass = null;

    private static final ThreadLocal<Boolean> DISABLE_PROXY_GENERATION = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    private static synchronized void createProxyClass(ClassResourceInfo cri) {
        if (classResourceInfoProxyClass == null) {
            ProxyFactory f = new ProxyFactory();
            f.setSuperclass(cri.getClass());
            f.setFilter(new MethodFilter() {
                public boolean isHandled(Method m) {
                    return true;
                }
            });
            classResourceInfoProxyClass = f.createClass();
        }
    }

    /**
     * Creates the class resource info proxy
     *
     * @param classResourceInfo the class resource info
     * @param generatorParams the generator params
     * @return the class resource info
     */
    public static ClassResourceInfo createProxy(ClassResourceInfo classResourceInfo, Class<?> generatorTypes[], Object generatorParams[]) {

        if (!DISABLE_PROXY_GENERATION.get()) {
            try {
                createProxyClass(classResourceInfo);
                ClassResourceInfo result = (ClassResourceInfo) classResourceInfoProxyClass.newInstance();
                CriProxyMethodHandler methodHandler = new CriProxyMethodHandler(result, generatorTypes, generatorParams);
                ((Proxy)result).setHandler(methodHandler);
                methodHandler.delegate = classResourceInfo;
                methodHandler.generatorTypes = generatorTypes;
                methodHandler.generatorParams = generatorParams;
                return result;
            } catch (Exception e) {
                LOGGER.error("Unable to create ClassResourceInfo proxy for {}", e, classResourceInfo);
            }
        }

        return classResourceInfo;
    }

    public static void reloadClassResourceInfo(ClassResourceInfo classResourceInfoProxy) {
        try {
            DISABLE_PROXY_GENERATION.set(true);
            CriProxyMethodHandler criMethodHandler = (CriProxyMethodHandler) ((ProxyObject)classResourceInfoProxy).getHandler();
            ClassResourceInfo newClassResourceInfo = (ClassResourceInfo) ReflectionHelper.invoke(null, ResourceUtils.class, "createClassResourceInfo",
                    criMethodHandler.generatorTypes, criMethodHandler.generatorParams);
            ClassResourceInfo oldClassResourceInfo = criMethodHandler.delegate;
            ResourceProvider resourceProvider = oldClassResourceInfo.getResourceProvider();
            updateResourceProvider(resourceProvider);
            newClassResourceInfo.setResourceProvider(resourceProvider);
            criMethodHandler.delegate = newClassResourceInfo;
        } catch (Exception e) {
            LOGGER.error("reloadClassResourceInfo() exception {}", e.getMessage());
        } finally {
            DISABLE_PROXY_GENERATION.remove();
        }
    }

    private static void updateResourceProvider(ResourceProvider resourceProvider) {
        if (resourceProvider.getClass().getName().equals("org.apache.cxf.jaxrs.spring.SpringResourceFactory")){
            try {
                ReflectionHelper.invoke(resourceProvider, resourceProvider.getClass(), "clearSingletonInstance", null, null);
            } catch (Exception e) {
                LOGGER.error("updateResourceProvider() clearSingletonInstance failed. {}", e);
            }
        }
    }

    public static class CriProxyMethodHandler implements MethodHandler {

        ClassResourceInfo delegate;
        Object[] generatorParams;
        Class<?>[] generatorTypes;

        public CriProxyMethodHandler(ClassResourceInfo delegate, Class<?> generatorTypes[], Object[] generatorParams) {
            this.generatorTypes = generatorTypes; }

        public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {
            // simple delegate to delegate object
            if (method.getName() == "setResourceProvider" &&
                    args != null &&
                    args[0] != null &&
                    args[0] instanceof SingletonResourceProvider) {
                try {
                    SingletonResourceProvider resourceProvider = (SingletonResourceProvider) args[0];
                    ClassLoader pluginClassLoader = delegate.getServiceClass().getClassLoader();
                    Object pluginInstance = PluginManager.getInstance().getPlugin(CxfJAXRSPlugin.class.getName(), pluginClassLoader);
                    if (pluginInstance != null) {
                        ReflectionHelper.invoke(pluginInstance, pluginInstance.getClass(),
                                "registerServiceInstance", new Class[] {Object.class}, resourceProvider.getInstance(null));
                    } else {
                      LOGGER.error("registerServiceInstance() CxfJAXRSPlugin not found in classLoader {}", pluginClassLoader);
                    }
                } catch (Exception e) {
                    LOGGER.error("registerServiceInstance() exception {}", e);
                }
            }
            return method.invoke(delegate, args);
        }
    }
}
