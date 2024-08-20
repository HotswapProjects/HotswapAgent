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
package org.hotswap.agent.plugin.mybatis.transformers;

import org.apache.ibatis.javassist.bytecode.AccessFlag;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.NewExpr;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.MyBatisPlugin;
import org.hotswap.agent.plugin.mybatis.proxy.ConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.proxy.SpringMybatisConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.util.ClassUtils;
import org.hotswap.agent.plugin.mybatis.util.XMLConfigBuilderUtils;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for MyBatis plugin.
 *
 * @author Vladimir Dvorak
 */
public class MyBatisTransformers {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisTransformers.class);

    public static final String SRC_FILE_NAME_FIELD = "$$ha$srcFileName";
    public static final String REFRESH_DOCUMENT_METHOD = "$$ha$refreshDocument";
    public static final String REFRESH_METHOD = "$$ha$refresh";
    public static final String IN_RELOAD_FIELD = "$$ha$inReload";

    public static final String INITIALIZED_FIELD = "$$ha$initialized";
    public static final String FACTORYBEAN_FIELD = "$$ha$factoryBean";
    public static final String FACTORYBEAN_SET_METHOD = "$$ha$setFactoryBean";
    public static final String CONFIGURATION_PROXY_METHOD = "$$ha$proxySqlSessionFactoryConfiguration";
    public static final String IS_MYBATIS_PLUS = "$$ha$isMybatisPlus";

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.parsing.XPathParser")
    public static void patchXPathParser(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtClass stringClass = classPool.get("java.lang.String");
        CtField sourceFileField = new CtField(stringClass, SRC_FILE_NAME_FIELD, ctClass);
        ctClass.addField(sourceFileField);

        CtMethod method = ctClass.getDeclaredMethod("createDocument");
        method.insertBefore("{" +
                "this." + SRC_FILE_NAME_FIELD + " = " + org.hotswap.agent.util.IOUtils.class.getName() + ".extractFileNameFromInputSource($1);" +
                "}"
        );
        CtMethod newMethod = CtNewMethod.make(
                "public boolean " + REFRESH_DOCUMENT_METHOD + "() {" +
                        "if(this." + SRC_FILE_NAME_FIELD + "!=null) {" +
                        "this.document=createDocument(new org.xml.sax.InputSource(new java.io.FileReader(this." + SRC_FILE_NAME_FIELD + ")));" +
                        "return true;" +
                        "}" +
                        "return false;" +
                        "}", ctClass);
        ctClass.addMethod(newMethod);
        LOGGER.debug("org.apache.ibatis.parsing.XPathParser patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.BaseBuilder")
    public static void patchBaseBuilder(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("org.apache.ibatis.builder.BaseBuilder patched.");
        CtField configField = ctClass.getField("configuration");
        configField.setModifiers(configField.getModifiers() & ~AccessFlag.FINAL);
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.xml.XMLConfigBuilder")
    public static void patchXMLConfigBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("this.configuration = " + ConfigurationProxy.class.getName() + ".getWrapper(this).proxy(this.configuration);");
        src.append("}");

        XMLConfigBuilderUtils.getBuilderInstrumentConstructor(ctClass, classPool).insertAfter(src.toString());
        CtMethod newMethod = CtNewMethod.make(
                "public void " + REFRESH_METHOD + "() {" +
                        "if(" + XPathParserCaller.class.getName() + ".refreshDocument(this.parser)) {" +
                        "this.parsed=false;" +
                        "parse();" +
                        "}" +
                        "}", ctClass);
        ctClass.addMethod(newMethod);
        LOGGER.debug("org.apache.ibatis.builder.xml.XMLConfigBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.xml.XMLMapperBuilder")
    public static void patchXMLMapperBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtField isMybatisPlusField = new CtField(classPool.get(boolean.class.getName()), IS_MYBATIS_PLUS, ctClass);
        ClassUtils.addFieldNotExists(ctClass, isMybatisPlusField);

        StringBuilder src = new StringBuilder("{");
        src.append("if(!" + IS_MYBATIS_PLUS + "){");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("} }");

        CtClass[] constructorParams = new CtClass[]{
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("org.apache.ibatis.session.Configuration"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Map")
        };

        CtConstructor constructor = ctClass.getDeclaredConstructor(constructorParams);
        constructor.insertAfter(src.toString());
        LOGGER.debug("org.apache.ibatis.builder.xml.XMLMapperBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.session.SqlSessionFactoryBuilder")
    public static void patchSqlSessionFactoryBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        // add $$ha$factoryBean field
        CtClass objClass = classPool.get("java.lang.Object");
        CtField factoryBeanField = new CtField(objClass, FACTORYBEAN_FIELD, ctClass);
        ctClass.addField(factoryBeanField);

        CtMethod setMethod = CtNewMethod.make(
                "public void " + FACTORYBEAN_SET_METHOD + "(Object factoryBean) {" +
                        "this." + FACTORYBEAN_FIELD + " = factoryBean;" +
                        "}", ctClass);
        ctClass.addMethod(setMethod);

        CtMethod buildMethod = ctClass.getDeclaredMethod("build",
                new CtClass[] {classPool.get("org.apache.ibatis.session.Configuration")});
        buildMethod.insertBefore("{" +
                "if (this." + FACTORYBEAN_FIELD + " != null) {" +
                "config = " + SqlSessionFactoryBeanCaller.class.getName() + ".proxyConfiguration(this." + FACTORYBEAN_FIELD + ", config);" +
                "}" +
                "}"
        );
        LOGGER.debug("org.apache.ibatis.session.SqlSessionFactoryBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.mybatis.spring.SqlSessionFactoryBean")
    public static void patchSqlSessionFactoryBean(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        // add $$ha$initialized field
        CtClass booleanClass = classPool.get(boolean.class.getName());
        CtField sourceFileField = new CtField(booleanClass, INITIALIZED_FIELD, ctClass);
        ctClass.addField(sourceFileField);

        CtMethod method = ctClass.getDeclaredMethod("afterPropertiesSet");
        method.insertAfter("{" +
                "this." + INITIALIZED_FIELD + " = true;" +
                "}"
        );

        CtConstructor constructor = ctClass.getDeclaredConstructor(new CtClass[] {});
        constructor.insertAfter("{" +
                SqlSessionFactoryBeanCaller.class.getName() + ".setFactoryBean(this.sqlSessionFactoryBuilder, this);" +
                "}");

        CtMethod proxyMethod = CtNewMethod.make(
                "public org.apache.ibatis.session.Configuration " + CONFIGURATION_PROXY_METHOD + "(org.apache.ibatis.session.Configuration configuration) {" +
                        "if(this." + INITIALIZED_FIELD + ") {" +
                        "return configuration;" +
                        "} else {" +
                        "return " + SpringMybatisConfigurationProxy.class.getName() + ".getWrapper(this).proxy(configuration);" +
                        "}" +
                        "}", ctClass);
        ctClass.addMethod(proxyMethod);
        LOGGER.debug("org.mybatis.spring.SqlSessionFactoryBean patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.mybatis.spring.mapper.MapperScannerConfigurer")
    public static void patchMapperScannerConfigurer(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        /*
         * In org.mybatis.spring.mapper.MapperScannerConfigurer#processPropertyPlaceHolders,
         * a BeanFactory is created using new DefaultListableBeanFactory() that contains only
         * this mapper scanner and processes the factory. This is not needed;
         * it should be removed from SpringChangedAgent after processing.
         */
        CtMethod processPropertyPlaceHolderM = ctClass.getDeclaredMethod("processPropertyPlaceHolders");
        processPropertyPlaceHolderM.addLocalVariable("tmpFactoryList", classPool.get("java.util.List"));
        processPropertyPlaceHolderM.insertBefore("{ tmpFactoryList = new java.util.ArrayList(); }");

        // Add the instance of DefaultListableBeanFactory to a list after its creation so that it can be removed later
        processPropertyPlaceHolderM.instrument(new ExprEditor() {
            @Override
            public void edit(NewExpr e) throws CannotCompileException {
                if (e.getClassName().equals("org.springframework.beans.factory.support.DefaultListableBeanFactory")) {
                    e.replace("{ $_ = $proceed($$); tmpFactoryList.add($_); }");
                }
            }
        });

        String defaultListableBeanFactoryPath = "org.springframework.beans.factory.support.DefaultListableBeanFactory";
        processPropertyPlaceHolderM.insertAfter("{ " +
                "    for (java.util.Iterator it = tmpFactoryList.iterator(); it.hasNext(); ) {\n" +
                defaultListableBeanFactoryPath + " factory = (" + defaultListableBeanFactoryPath + ") it.next();\n" +
                "        org.hotswap.agent.plugin.spring.reload.SpringChangedAgent.destroyBeanFactory(factory);\n" +
                "    }\n" +
                " }");

        LOGGER.debug("org.mybatis.spring.mapper.MapperScannerConfigurer patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.session.Configuration")
    public static void patchConfiguration(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtClass booleanClass = classPool.get(boolean.class.getName());
        CtField onReloadField = new CtField(booleanClass, IN_RELOAD_FIELD, ctClass);
        onReloadField.setModifiers(Modifier.PUBLIC);
        ctClass.addField(onReloadField);

        // If $$ha$inReload is true, then we need to remove the old entry.
        CtMethod isResourceLoadedMethod = ctClass.getDeclaredMethod("isResourceLoaded", new CtClass[]{
                classPool.get(String.class.getName())
        });

        isResourceLoadedMethod.insertBefore("{\n" +
                "if(" + IN_RELOAD_FIELD + "){\n" +
                "this.loadedResources.remove($1);" +
                "}\n" +
                "}");

        LOGGER.debug("org.apache.ibatis.session.Configuration patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.session.Configuration\\$StrictMap")
    public static void patchStrictMap(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {

        // To avoid xxx collection already contains value for xxx.
        CtMethod method = ctClass.getDeclaredMethod("put", new CtClass[]{
                classPool.get(String.class.getName()), classPool.get(Object.class.getName())
        });
        method.insertBefore("if(containsKey($1)){remove($1);}");

        LOGGER.debug("org.apache.ibatis.session.Configuration$StrictMap patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.reflection.DefaultReflectorFactory")
    public static void patchDefaultReflectorFactory(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod findForClass = ctClass.getDeclaredMethod("findForClass");
        findForClass.insertBefore("{" +
                "    $0.reflectorMap.remove($1);" +
                "}");

        LOGGER.debug("org.apache.ibatis.reflection.DefaultReflectorFactory patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.binding.MapperRegistry")
    public static void patchMapperRegistry(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod hasMapperM = ctClass.getDeclaredMethod("hasMapper", new CtClass[]{classPool.get(Class.class.getName())});
        hasMapperM.insertBefore("{" +
                "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                "    knownMappers.remove($1);\n" +
                "}\n" +
                "}");

        LOGGER.debug("org.apache.ibatis.binding.MapperRegistry patched.");
    }
}
