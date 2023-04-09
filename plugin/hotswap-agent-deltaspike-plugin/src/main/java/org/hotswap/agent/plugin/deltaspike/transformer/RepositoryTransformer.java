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
package org.hotswap.agent.plugin.deltaspike.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.cdi.HaCdiCommons;
import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook and patch Repository classes
 *
 * @author Vladimir Dvorak
 */
public class RepositoryTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RepositoryTransformer.class);

    public static final String REINITIALIZE_METHOD = "$$ha$reinitialize";

    /**
     * Register DeltaspikePlugin and add reinitialization method to RepositoryComponent (ds<1.9)
     *
     * @param classPool the class pool
     * @param ctClass   the ct class
     * @throws CannotCompileException the cannot compile exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.data.impl.meta.RepositoryComponent")
    public static void patchRepositoryComponent(ClassPool classPool, CtClass ctClass) throws CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DeltaSpikePlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(DeltaSpikePlugin.class, "registerRepoComponent",
                "this", "java.lang.Object",
                "this.repoClass", "java.lang.Class"));
        src.append("}");

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        ctClass.addMethod(CtNewMethod.make("public void " + REINITIALIZE_METHOD + "() {" +
                "   this.methods.clear(); " +
                "   initialize();" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.data.impl.meta.RepositoryComponent - registration hook and reinitialization method added.");
    }


    /**
     * Register DeltaspikePlugin and add reinitialization method to RepositoryMetadataHandler. (ds>=1.9)
     *
     * @param classPool the class pool
     * @param ctClass   the ctclass
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException      the not found exception
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.data.impl.meta.RepositoryMetadataHandler")
    public static void patchRepositoryMetadataHandler(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }
        ctClass.addMethod(CtNewMethod.make("public void " + REINITIALIZE_METHOD + "(java.lang.Class repositoryClass) {" +
                    "org.apache.deltaspike.data.impl.meta.RepositoryMetadata metadata = metadataInitializer.init(repositoryClass, this.beanManager);" +
                    "this.repositoriesMetadata.put(repositoryClass, metadata);" +
                "}", ctClass));

        LOGGER.debug("org.apache.deltaspike.data.impl.meta.RepositoryMetadataHandler - registration hook and reinitialization method added.");
    }

    /**
     * Register DeltaspikePlugin and register repository classes.
     *
     * @param ctClass the ctclass
     * @throws CannotCompileException the cannot compile exception
     * @throws NotFoundException
     * @throws org.hotswap.agent.javassist.CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.deltaspike.data.impl.RepositoryExtension")
    public static void patchRepositoryExtension(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException, org.hotswap.agent.javassist.CannotCompileException {
        if (HaCdiCommons.isJakarta(classPool)) {
            return;
        }

        if (ctClass.getSuperclassName().equals(Object.class.getName())) {
            ctClass.setSuperclass(classPool.get(HaAfteBeanDiscovery.class.getName()));
        } else {
            LOGGER.error("org.apache.deltaspike.data.impl.RepositoryExtension patch failed. Expected superclass java.lang.Object, found:" + ctClass.getSuperclassName());
        }

        LOGGER.debug("org.apache.deltaspike.data.impl.RepositoryExtension - registration hook and registration repository classes added.");
    }
}
