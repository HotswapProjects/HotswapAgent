/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.deltaspike_jakarta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike_jakarta.command.PartialBeanClassRefreshCommand;
import org.hotswap.agent.plugin.deltaspike_jakarta.command.RepositoryRefreshCommand;
import org.hotswap.agent.plugin.deltaspike_jakarta.transformer.DeltaSpikeProxyTransformer;
import org.hotswap.agent.plugin.deltaspike_jakarta.transformer.DeltaspikeContextsTransformer;
import org.hotswap.agent.plugin.deltaspike_jakarta.transformer.PartialBeanExtensionTransformer;
import org.hotswap.agent.plugin.deltaspike_jakarta.transformer.RepositoryTransformer;
import org.hotswap.agent.util.AnnotationHelper;

/**
 * Apache DeltaSpike Jakarta
 * @author Vladimir Dvorak
 */
@Plugin(name = "DeltaspikeJakarta",
        description = "Apache DeltaSpike (http://deltaspike.apache.org/), support repository reloading",
        testedVersions = {"2.0.0"},
        expectedVersions = {"2.0.x"},
        supportClass = {
            DeltaSpikeProxyTransformer.class, PartialBeanExtensionTransformer.class, RepositoryTransformer.class, DeltaspikeContextsTransformer.class
        }
)
public class DeltaSpikeJakartaPlugin
{
    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaSpikeJakartaPlugin.class);

    private static final String REPOSITORY_ANNOTATION = "org.apache.deltaspike.data.api.Repository";
    public static final int WAIT_ON_REDEFINE = 500;

    @Init
    ClassLoader appClassLoader;

    @Init
    Scheduler scheduler;
    @Init
    PluginConfiguration pluginConfiguration;

    boolean initialized = false;
    int waitOnRedefine = WAIT_ON_REDEFINE;

    Map<Class, Boolean> registeredPartialBeanClasses = new WeakHashMap<>();
    Map<Object, String> registeredRepoProxies = new WeakHashMap<>();
    List<Class<?>> repositoryClasses;

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        if (!initialized) {
            LOGGER.info("DeltaspikeJakarta plugin initialized.");
            initialized = true;
            waitOnRedefine = Integer.valueOf(pluginConfiguration.getProperty("deltaspike.waitOnRedefine", String.valueOf(WAIT_ON_REDEFINE)));
        }
    }

    public void registerRepositoryClasses(List<Class<?>> repositoryClassesList) {
        this.repositoryClasses = new ArrayList<>(repositoryClassesList);
    }

    public void registerRepoProxy(Object repoProxy, Class<?> repositoryClass) {
        if (repositoryClasses == null) {
            return;
        }
        if (!registeredRepoProxies.containsKey(repoProxy)) {
            LOGGER.debug("DeltaspikeJakartaPlugin - repository proxy registered : {}", repositoryClass.getName());
        }
        Class<?> checkedClass = repositoryClass;
        while(checkedClass != null && !repositoryClasses.contains(checkedClass)) {
            checkedClass = checkedClass.getSuperclass();
        }
        if (checkedClass != null) {
            registeredRepoProxies.put(repoProxy, repositoryClass.getName());
        }
    }

    public void registerPartialBean(Class<?> partialBeanClass) {
        synchronized(registeredPartialBeanClasses) {
            registeredPartialBeanClasses.put(partialBeanClass, Boolean.TRUE);
        }
        LOGGER.debug("Partial bean '{}' registered", partialBeanClass.getName());
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(CtClass clazz, Class original, ClassPool classPool) throws NotFoundException {
        PartialBeanClassRefreshCommand cmd = checkRefreshPartialBean(clazz, original);
        if (cmd != null) {
            checkRefreshRepository(clazz, classPool, cmd);
        }
    }

    private PartialBeanClassRefreshCommand checkRefreshPartialBean(CtClass clazz, Class original) throws NotFoundException {
        PartialBeanClassRefreshCommand cmd = null;
        if (registeredPartialBeanClasses.containsKey(original)) {
            String oldSignForProxyCheck = DeltaspikeClassSignatureHelper.getSignaturePartialBeanClass(original);
            cmd = new PartialBeanClassRefreshCommand(appClassLoader, original, oldSignForProxyCheck, scheduler);
            scheduler.scheduleCommand(cmd, waitOnRedefine);
        }
        return cmd;
    }

    private void checkRefreshRepository(CtClass clazz, ClassPool classPool, PartialBeanClassRefreshCommand masterCmd) throws NotFoundException {
        if (isRepository(clazz, classPool)) {
            RepositoryRefreshCommand cmd = null;
            if (repositoryClasses!= null) {
                cmd = new RepositoryRefreshCommand(appClassLoader, clazz.getName(), getRepositoryProxies(clazz.getName()));
            }
            if (cmd != null) {
                masterCmd.addChainedCommand(cmd);
            }
        }
    }

    private List<Object> getRepositoryProxies(String repositoryClassName) {
        List<Object> result = new ArrayList<>();
        for (Entry<Object, String> entry: registeredRepoProxies.entrySet()) {
            if (repositoryClassName.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private boolean isRepository(CtClass clazz, ClassPool classPool) throws NotFoundException {
        if (isSyntheticCdiClass(clazz.getName())) {
            return false;
        }
        CtClass ctInvocationHandler = classPool.get("java.lang.reflect.InvocationHandler");
        if (clazz.subtypeOf(ctInvocationHandler)) {
            return false;
        }
        if (AnnotationHelper.hasAnnotation(clazz, REPOSITORY_ANNOTATION)) {
            return true;
        }
        CtClass superClass = clazz.getSuperclass();
        if (superClass != null) {
            return isRepository(superClass, classPool);
        }
        return false;
    }

    private Object getObjectByName(Map<Object, String> registeredComponents, String className) {
        for (Entry<Object, String> entry : registeredComponents.entrySet()) {
           if (className.equals(entry.getValue())) {
               return entry.getKey();
           }
        }
        return null;
    }

    private boolean isSyntheticCdiClass(String className) {
        return className.contains("$$");
    }

}
