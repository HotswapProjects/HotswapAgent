package org.hotswap.agent.plugin.mybatisplus.transformers;

import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.javassist.expr.Cast;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.NewExpr;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatisplus.MyBatisPlusPlugin;
import org.hotswap.agent.plugin.mybatisplus.proxy.ConfigurationPlusProxy;
import org.hotswap.agent.plugin.mybatis.proxy.SpringMybatisConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.transformers.XPathParserCaller;
import org.hotswap.agent.plugin.mybatis.util.ClassUtils;
import org.hotswap.agent.plugin.mybatis.util.XMLConfigBuilderUtils;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers.*;

/**
 * Static transformers for MyBatis plugin-Mybatis-Plus.
 */
public class MyBatisPlusTransformers {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlusTransformers.class);

    private static boolean isMybatisPlusFlag = false;
    public static final String HA_SQLSESSIONFACTORY_BUILDER_FIELD = "$$ha$sqlSessionFactoryBuilder";

    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.xml.XMLMapperBuilder")
    public static void patchXMLMapperBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtField isMybatisPlusField = new CtField(classPool.get(boolean.class.getName()), IS_MYBATIS_PLUS, ctClass);
        ClassUtils.addFieldNotExists(ctClass, isMybatisPlusField);

        StringBuilder src = new StringBuilder("{");
        src.append("if(" + IS_MYBATIS_PLUS + "){");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlusPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlusPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("} }");

        CtClass[] constructorParams = new CtClass[]{
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("org.apache.ibatis.session.Configuration"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Map")
        };

        CtConstructor constructor = ctClass.getDeclaredConstructor(constructorParams);
        if (isMybatisPlusFlag) {
            constructor.insertBefore("{ " + IS_MYBATIS_PLUS + "=true; }");
        }

        constructor.insertAfter(src.toString());
        LOGGER.debug("org.apache.ibatis.builder.xml.XMLMapperBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisXMLMapperBuilder")
    public static void patchPlusXMLMapperBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlusPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlusPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("}");

        CtClass[] constructorParams = new CtClass[]{
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("org.apache.ibatis.session.Configuration"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Map")
        };

        CtConstructor constructor = ctClass.getDeclaredConstructor(constructorParams);
        constructor.insertAfter(src.toString());

        LOGGER.debug("com.baomidou.mybatisplus.core.MybatisXMLMapperBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisXMLConfigBuilder")
    public static void patchMybatisXMLConfigBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlusPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlusPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("this.configuration = " + ConfigurationPlusProxy.class.getName() + ".getWrapper(this).proxy(this.configuration);");
        src.append("}");


        CtConstructor instrumentConstructor = XMLConfigBuilderUtils.getBuilderInstrumentConstructor(ctClass, classPool);
        instrumentConstructor.insertAfter(src.toString());

        CtMethod newMethod = CtNewMethod.make(
                "public void " + REFRESH_METHOD + "() {" +
                        "if(" + XPathParserCaller.class.getName() + ".refreshDocument(this.parser)) {" +
                        "this.parsed=false;" +
                        "parse();" +
                        "}" +
                        "}", ctClass);
        ctClass.addMethod(newMethod);

        LOGGER.debug("com.baomidou.mybatisplus.core.MybatisXMLConfigBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder")
    public static void patchPlusSqlSessionFactoryBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        // add $$ha$factoryBean field
        isMybatisPlusFlag = true;
        CtClass objClass = classPool.get("java.lang.Object");
        CtField factoryBeanField = new CtField(objClass, FACTORYBEAN_FIELD, ctClass);
        ctClass.addField(factoryBeanField);

        CtMethod setMethod = CtNewMethod.make(
                "public void " + FACTORYBEAN_SET_METHOD + "(Object factoryBean) {" +
                        "this." + FACTORYBEAN_FIELD + " = factoryBean;" +
                        "}", ctClass);
        ctClass.addMethod(setMethod);

        CtMethod buildMethod = ctClass.getDeclaredMethod("build",
                new CtClass[]{classPool.get("org.apache.ibatis.session.Configuration")});

        String wrapperCode = "{" +
                "if (this." + FACTORYBEAN_FIELD + " != null) {" +
                "$1 = " + PlusSqlSessionFactoryBeanCaller.class.getName() + ".proxyPlusConfiguration(this." + FACTORYBEAN_FIELD + ", $1);" +
                "}" +
                "}";
        AtomicBoolean  existCast = new AtomicBoolean(false);
        buildMethod.instrument(new ExprEditor() {
            @Override
            public void edit(Cast c) throws CannotCompileException {
                // 判断是否是 MybatisConfiguration 的强制转换
                try {
                    String name = c.getType().getName();
                    LOGGER.info("cast: {}", name);
                    if (name.equals("com.baomidou.mybatisplus.core.MybatisConfiguration")) {
                        existCast.set(true);
                        c.replace("{ " +
                                "$_ = $proceed($$); " +
                                "if (this." + FACTORYBEAN_FIELD + " != null) {" +
                                "$1 = " + PlusSqlSessionFactoryBeanCaller.class.getName() + ".proxyPlusConfiguration(this." + FACTORYBEAN_FIELD + ", $_);" +
                                "}" +
                                "}");
                    }
                } catch (NotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        if (!existCast.get()) {
            buildMethod.insertBefore(wrapperCode);
        }

        LOGGER.debug("com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean")
    public static void patchPlusSqlSessionFactoryBean(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        // add $$ha$initialized field
        CtClass booleanClass = classPool.get(boolean.class.getName());
        CtField sourceFileField = new CtField(booleanClass, INITIALIZED_FIELD, ctClass);
        ctClass.addField(sourceFileField);

        CtField buildField = new CtField(classPool.get(SqlSessionFactoryBuilder.class.getName()),
                HA_SQLSESSIONFACTORY_BUILDER_FIELD, ctClass);
        ctClass.addField(buildField);

        CtMethod afterPropertiesMethod = ctClass.getDeclaredMethod("afterPropertiesSet");
        afterPropertiesMethod.insertAfter("{" +
                "this." + INITIALIZED_FIELD + " = true;" +
                "}"
        );

        if (ClassUtils.fieldExists(ctClass, "sqlSessionFactoryBuilder")) {
            CtConstructor constructor = ctClass.getDeclaredConstructor(new CtClass[]{});
            constructor.insertAfter("{" +
                    PlusSqlSessionFactoryBeanCaller.class.getName() + ".setPlusFactoryBean(this.sqlSessionFactoryBuilder, this);" +
                    "}");
        } else {
            CtMethod buildSqlSessionFactoryM = ctClass.getDeclaredMethod("buildSqlSessionFactory");
            buildSqlSessionFactoryM.instrument(new ExprEditor() {
                @Override
                public void edit(NewExpr e) throws CannotCompileException {
                    if (e.getClassName().equals("com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder")) {
                        e.replace("{ $_ = $proceed($$); " +
                                HA_SQLSESSIONFACTORY_BUILDER_FIELD + "=$_;" +
                                PlusSqlSessionFactoryBeanCaller.class.getName() + ".setPlusFactoryBean(this." + HA_SQLSESSIONFACTORY_BUILDER_FIELD + ", this);" +
                                " }");
                    }
                }
            });
        }

        CtMethod proxyMethod = CtNewMethod.make(
                "public org.apache.ibatis.session.Configuration " + CONFIGURATION_PROXY_METHOD + "(org.apache.ibatis.session.Configuration configuration) {" +
                        "if(this." + INITIALIZED_FIELD + ") {" +
                        "return configuration;" +
                        "} else {" +
                        "return " + SpringMybatisConfigurationProxy.class.getName() + ".getWrapper(this).proxy(configuration);" +
                        "}" +
                        "}", ctClass);
        ctClass.addMethod(proxyMethod);
        LOGGER.debug("com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisConfiguration\\$StrictMap")
    public static void patchPlusStrictMap(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        // To avoid xxx collection already contains value for xxx.
        CtMethod method = ctClass.getDeclaredMethod("put", new CtClass[]{
                classPool.get(String.class.getName()), classPool.get(Object.class.getName())
        });
        method.insertBefore("if(containsKey($1)){remove($1);}");

        LOGGER.debug("com.baomidou.mybatisplus.core.MybatisConfiguration$StrictMap patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisConfiguration")
    public static void patchPlusConfiguration(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        isMybatisPlusFlag = true;
        CtMethod removeMappedStatementMethod = CtNewMethod.make("public void $$removeMappedStatement(String statementName){" +
                "mappedStatements.remove(statementName);" +
                "}", ctClass);
        ctClass.addMethod(removeMappedStatementMethod);

        ctClass.getDeclaredMethod("addMappedStatement", new CtClass[]{
                classPool.get("org.apache.ibatis.mapping.MappedStatement")
        }).insertBefore("$$removeMappedStatement($1.getId());");

        CtClass[] params = {
                classPool.get(String.class.getName()), classPool.get(boolean.class.getName())};
        if (ClassUtils.methodExists(ctClass, "hasStatement", params)) {
            CtMethod hasStatementM = ctClass.getDeclaredMethod("hasStatement", params);
            hasStatementM.insertBefore("{" +
                    "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                    "$$removeMappedStatement($1);\n" +
                    "}\n" +
                    "}");
        }else {
            CtMethod hasStatementM = CtNewMethod.make("public boolean hasStatement(String statementName, boolean validateIncompleteStatements) {" +
                    "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                    "$$removeMappedStatement($1);\n" +
                    "}\n" +
                    "return super.hasStatement($1, $2);" +
                    "}", ctClass);
            ctClass.addMethod(hasStatementM);

       }

        LOGGER.debug("com.baomidou.mybatisplus.core.MybatisConfiguration patched.");
    }


    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisMapperRegistry")
    public static void patchMapperRegistry(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        isMybatisPlusFlag = true;
        CtMethod hasMapperM = ctClass.getDeclaredMethod("hasMapper", new CtClass[]{classPool.get(Class.class.getName())});
        hasMapperM.insertBefore("{" +
                "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                "    knownMappers.remove($1);\n" +
                "}\n" +
                "}");

        LOGGER.debug("com.baomidou.mybatisplus.core.MybatisMapperRegistry patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.injector.DefaultSqlInjector")
    public static void patchDefaultSqlInjector(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod inspectInjectM = CtNewMethod.make(
                "public void inspectInject(org.apache.ibatis.builder.MapperBuilderAssistant builderAssistant, java.lang.Class mapperClass) {" +
                        "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                        "com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getMapperRegistryCache(builderAssistant.getConfiguration()).remove($2.toString());\n" +
                        "}\n" +
                        "super.inspectInject(builderAssistant, mapperClass);" +
                        "}", ctClass);

        ctClass.addMethod(inspectInjectM);

        LOGGER.debug("com.baomidou.mybatisplus.core.injector.DefaultSqlInjector patched.");
    }


    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.toolkit.ReflectionKit")
    public static void patchPlusReflectionKit(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod getFieldListM = ctClass.getDeclaredMethod("getFieldList", new CtClass[]{
                classPool.get(Class.class.getName())});
        getFieldListM.insertBefore("{ " +
                "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                "    CLASS_FIELD_CACHE.remove($1);\n" +
                "}\n" +
                "}");

        LOGGER.debug("com.baomidou.mybatisplus.core.toolkit.ReflectionKit patched.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.metadata.TableInfoHelper")
    public static void patchPlusTableInfoHelper(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod initTableInfoM = ctClass.getDeclaredMethod("initTableInfo", new CtClass[]{
                classPool.get("org.apache.ibatis.builder.MapperBuilderAssistant"),
                classPool.get(Class.class.getName())
        });

        initTableInfoM.insertBefore("{ " +
                "if (org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands.reloadFlag) {\n" +
                "    TABLE_INFO_CACHE.remove($2);\n" +
                "}\n" +
                "}");

        LOGGER.debug("com.baomidou.mybatisplus.core.metadata.TableInfoHelper patched.");
    }
}
