package org.hotswap.agent.plugin.spring.boot.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.logging.AgentLogger;

import java.security.ProtectionDomain;

public class PropertySourceLoaderTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertySourceLoaderTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "org.springframework.boot.env.YamlPropertySourceLoader")
    public static void transformYamlPropertySourceLoader(CtClass clazz, ClassPool classPool, ClassLoader classLoader,
                                                         ProtectionDomain protectionDomain) throws NotFoundException, CannotCompileException {
        enhanceBasePropertySourceLoader(clazz);

        CtMethod ctMethod = clazz.getDeclaredMethod("load");
        if (ctMethod.getParameterTypes().length == 2) {
            ctMethod.addLocalVariable("_reload", classPool.get("org.hotswap.agent.plugin.spring.boot.env.Boot2YamlPropertySourceReload"));
            ctMethod.insertBefore("{_reload = new org.hotswap.agent.plugin.spring.boot.env.Boot2YamlPropertySourceReload($1, $2);}");
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getClassName().equals("org.springframework.boot.env.OriginTrackedYamlLoader")
                            && m.getMethodName().equals("load")) {
                        m.replace("{$_ = _reload.load();}");
                    }
//                    else if (m.getMethodName().equals("add")) {
//                        m.replace("{ $_ = $proceed($$); load0($1,_reload);}");
//                    }
                }
            });
            ctMethod.insertAfter("{ loadList0($_ , _reload);}");
        } else if (ctMethod.getParameterTypes().length == 3) {
            ctMethod.addLocalVariable("_reload", classPool.get("org.hotswap.agent.plugin.spring.boot.env.Boot1YamlPropertySourceReload"));
            ctMethod.insertBefore("{_reload = new org.hotswap.agent.plugin.spring.boot.env.Boot1YamlPropertySourceReload($1, $2, $3);}");
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getClassName().equals("org.springframework.boot.env.YamlPropertySourceLoader$Processor")
                            && m.getMethodName().equals("process")) {
                        m.replace("{$_ = _reload.load();}");
                    }
                }
            });
            ctMethod.insertAfter("{ load0($_ , _reload); }");
        }

        LOGGER.debug("Patch org.springframework.boot.env.YamlPropertySourceLoader success");
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.boot.env.PropertiesPropertySourceLoader")
    public static void transformPropertiesPropertySourceLoader(CtClass clazz, ClassPool classPool, ClassLoader classLoader,
                                                               ProtectionDomain protectionDomain) throws NotFoundException, CannotCompileException {
        enhanceBasePropertySourceLoader(clazz);

        CtMethod ctMethod = clazz.getDeclaredMethod("load");
        if (ctMethod.getParameterTypes().length == 2) {
            if (isSpringBoot2LowerVersion(clazz, classPool)) {
                ctMethod.addLocalVariable("_reload", classPool.get("org.hotswap.agent.plugin.spring.boot.env.Boot2LowVersionPropertiesPropertySourceReload"));
                ctMethod.insertBefore("{_reload = new org.hotswap.agent.plugin.spring.boot.env.Boot2LowVersionPropertiesPropertySourceReload($0, $1, $2);}");
            } else {
                ctMethod.addLocalVariable("_reload", classPool.get("org.hotswap.agent.plugin.spring.boot.env.Boot2PropertiesPropertySourceReload"));
                ctMethod.insertBefore("{_reload = new org.hotswap.agent.plugin.spring.boot.env.Boot2PropertiesPropertySourceReload($0, $1, $2);}");
            }
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("loadProperties")) {
                        m.replace("{$_ = _reload.load();}");
                    }
//                    else if (m.getMethodName().equals("add")) {
//                        m.replace("{ $_ = $proceed($$); load0($1,_reload); }");
//                    }
                }
            });
            ctMethod.insertAfter("{ loadList0($_ , _reload); }");
        } else if (ctMethod.getParameterTypes().length == 3) {
            ctMethod.addLocalVariable("_reload", classPool.get("org.hotswap.agent.plugin.spring.boot.env.Boot1PropertiesPropertySourceReload"));
            ctMethod.insertBefore("{_reload = new org.hotswap.agent.plugin.spring.boot.env.Boot1PropertiesPropertySourceReload($1, $2, $3);}");
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getClassName().equals("org.springframework.core.io.support.PropertiesLoaderUtils")
                            && m.getMethodName().equals("loadProperties")) {
                        m.replace("{$_ = _reload.load();}");
                    }
                }
            });
            ctMethod.insertAfter("{ load0($_ , _reload); }");
        }

        LOGGER.debug("Patch org.springframework.boot.env.YamlPropertySourceLoader success");
    }

    private static boolean isSpringBoot2LowerVersion(CtClass clazz, ClassPool classPool) {
        try {
            CtMethod ctMethod = clazz.getDeclaredMethod("loadProperties", new CtClass[]{classPool.get("org.springframework.core.io.Resource")});
            if ("java.util.Map".equals(ctMethod.getReturnType().getName())) {
                return true;
            } else {
                return false;
            }
        } catch (Exception t) {
            return true;
        }
    }


    private static void enhanceBasePropertySourceLoader(CtClass clazz) throws CannotCompileException {
        clazz.addMethod(CtMethod.make("private void load0(org.springframework.core.env.PropertySource p, " +
                "org.hotswap.agent.plugin.spring.api.PropertySourceReload r) throws java.io.IOException { " +
                "if (p instanceof org.hotswap.agent.plugin.spring.transformers.api.IReloadPropertySource) { " +
                "((org.hotswap.agent.plugin.spring.transformers.api.IReloadPropertySource) p).setReload(r); " +
                "} }", clazz));
        clazz.addMethod(CtMethod.make("private void loadList0(java.util.List ps, " +
                "org.hotswap.agent.plugin.spring.api.PropertySourceReload r) throws java.io.IOException { " +
                "for (int i=0;i< ps.size();i++) { " +
                "Object pp = ps.get(i);" +
                " if (pp instanceof org.springframework.core.env.PropertySource) {" +
                "load0((org.springframework.core.env.PropertySource)pp,r);" +
                "} } }", clazz));
    }
}
