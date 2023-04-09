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
package org.hotswap.agent.plugin.ibatis;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for IBatis plugin.
 *
 * @author muwaiwai
 * @date 2021-6-18
 */
public class IBatisTransformers {
    private static AgentLogger LOGGER = AgentLogger.getLogger(IBatisTransformers.class);
    public static final String REFRESH_METHOD = "$$ha$refresh";

    /**
     * Set the SqlMapConfigParser instance
     * @param ctClass
     * @param classPool
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    @OnClassLoadEvent(classNameRegexp = "com.ibatis.sqlmap.engine.builder.xml.SqlMapConfigParser")
    public static void patchSqlMapConfigParser(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        StringBuilder src = new StringBuilder("{");
        src.append(IBatisConfigurationHandler.class.getName() + ".setSqlMapConfigParser(this);");
        src.append("}");
        CtClass[] constructorParams = new CtClass[] {};
        ctClass.getDeclaredConstructor(constructorParams).insertAfter(src.toString());
        LOGGER.debug("com.ibatis.sqlmap.engine.builder.xml.SqlMapConfigParser patched.");
    }


    /**
     * Set ibatis sqlMap configLocation files
     * @param ctClass
     * @param classPool
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.orm.ibatis.SqlMapClientFactoryBean")
    public static void patchSqlMapClientFactoryBean(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(IBatisPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(IBatisPlugin.class, "registConfigFile",IBatisConfigurationHandler.class.getName() + ".toPath(this.configLocations)", "java.lang.String"));
        src.append(PluginManagerInvoker.buildCallPluginMethod(IBatisPlugin.class, "registConfigFile",IBatisConfigurationHandler.class.getName() + ".toPath(this.mappingLocations)", "java.lang.String"));
        src.append(IBatisConfigurationHandler.class.getName() + ".setMapFiles(this.configLocations,this.mappingLocations,this.sqlMapClientProperties);");
        src.append("}");
        CtMethod method = ctClass.getDeclaredMethod("afterPropertiesSet");
        method.insertAfter(src.toString());
        LOGGER.debug("org.springframework.orm.ibatis.SqlMapClientFactoryBean patched.");
    }

    /**
     * Set the XmlParserState instance
     * @param ctClass
     * @param classPool
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    @OnClassLoadEvent(classNameRegexp = "com.ibatis.sqlmap.engine.builder.xml.SqlMapParser")
    public static void  patchSqlMapParser(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        StringBuilder src = new StringBuilder("{");
        src.append(IBatisConfigurationHandler.class.getName() + ".setParserState(this.state);");
        src.append("}");
        CtClass[] constructorParams = new CtClass[] {
            classPool.get("com.ibatis.sqlmap.engine.builder.xml.XmlParserState")
        };
        ctClass.getDeclaredConstructor(constructorParams).insertAfter(src.toString());
        LOGGER.debug("com.ibatis.sqlmap.engine.builder.xml.SqlMapParser patched.");
    }

    /**
     * Clear the SqlMapExecutorDelegate state
     * @param ctClass
     * @param classPool
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "com.ibatis.sqlmap.engine.impl.SqlMapExecutorDelegate")
    public static void  patchSqlMapExecutorDelegate(CtClass ctClass, ClassPool classPool) throws CannotCompileException {
        CtMethod newMethod = CtNewMethod.make(
                "public void " + REFRESH_METHOD + "() {" +
                    "this.mappedStatements.clear();" +
                "}", ctClass);
        ctClass.addMethod(newMethod);
        LOGGER.debug("com.ibatis.sqlmap.engine.impl.SqlMapExecutorDelegate patched.");
    }
}
