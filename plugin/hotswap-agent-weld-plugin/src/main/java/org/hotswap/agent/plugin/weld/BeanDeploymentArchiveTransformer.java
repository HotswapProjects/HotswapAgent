package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into WeldBeanDeploymentArchive or BeanDeploymentArchiveImpl(WildFly) constructors to initialize WeldPlugin
 *
 * @author Vladimir Dvorak
 */
public class BeanDeploymentArchiveTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeploymentArchiveTransformer.class);

    /**
     * Basic WeldBeanDeploymentArchive transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {

        CtClass[] constructorParams = new CtClass[] {
            classPool.get("java.lang.String"),
            classPool.get("java.util.Collection"),
            classPool.get("org.jboss.weld.bootstrap.spi.BeansXml"),
            classPool.get("java.util.Set")
        };

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "init"));
        src.append("org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent.registerArchive(getClass().getClassLoader(), this, null);");
        src.append("}");

        CtConstructor declaredConstructor = clazz.getDeclaredConstructor(constructorParams);
        declaredConstructor.insertAfter(src.toString());

        LOGGER.debug("Class '{}' patched with BDA registration.", clazz.getName());
    }

    /**
     * Should be moved to a separate module just for wildfly. Note that cdi 1.1+
     * can scan an archive and consider it as a cdi deployment even without a
     * beans.xml (implicit)
     *
     * Jboss BeanDeploymentArchiveImpl transformation.
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl")
    public static void transformJbossBda(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append("if (beansXml!=null && beanArchiveType!=null && (\"EXPLICIT\".equals(beanArchiveType.toString()) || \"IMPLICIT\".equals(beanArchiveType.toString()))){");
        src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class, "module.getClassLoader()"));
        src.append(PluginManagerInvoker.buildCallPluginMethod("module.getClassLoader()", WeldPlugin.class, "initInJBossAS"));
        src.append("    Class agC = Class.forName(\"org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent\", true, module.getClassLoader());");
        src.append("    java.lang.reflect.Method agM  = agC.getDeclaredMethod(\"registerArchive\", new Class[] {java.lang.ClassLoader.class, org.jboss.weld.bootstrap.spi.BeanDeploymentArchive.class, java.lang.String.class});");
        src.append("    agM.invoke(null, new Object[] { module.getClassLoader(),this, beanArchiveType.toString()});");
        src.append("}}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertAfter(src.toString());
        }

        LOGGER.debug("Class 'org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl' patched with BDA registration.");
    }

}
