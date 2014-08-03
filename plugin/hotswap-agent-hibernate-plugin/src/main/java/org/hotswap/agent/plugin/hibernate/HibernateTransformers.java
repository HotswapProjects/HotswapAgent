package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.bytecode.AccessFlag;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate.proxy.SessionFactoryProxy;

/**
 * Static transformers for Hibernate plugin.
 */
public class HibernateTransformers {
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernateTransformers.class);

    /**
     * Override HibernatePersistence.createContainerEntityManagerFactory() to return EntityManagerFactory proxy object.
     * {@link org.hotswap.agent.plugin.hibernate.proxy.EntityManagerFactoryProxy} holds reference to all proxied factories
     * and on refresh command replaces internal factory with fresh instance.
     * <p/>
     * Two variants covered - createContainerEntityManagerFactory and createEntityManagerFactory.
     * <p/>
     * After the entity manager factory and it's proxy are instantiated, plugin init method is invoked.
     */
    @Transform(classNameRegexp = "(org.hibernate.ejb.HibernatePersistence)|(org.hibernate.jpa.HibernatePersistenceProvider)")
    public static void proxyHibernatePersistence(CtClass clazz) throws Exception {
        LOGGER.debug("Override org.hibernate.ejb.HibernatePersistence#createContainerEntityManagerFactory and createEntityManagerFactory to create a EntityManagerFactoryProxy proxy.");

        CtMethod oldMethod = clazz.getDeclaredMethod("createContainerEntityManagerFactory");
        oldMethod.setName("_createContainerEntityManagerFactory" + clazz.getSimpleName());
        CtMethod newMethod = CtNewMethod.make(
                "public javax.persistence.EntityManagerFactory createContainerEntityManagerFactory(" +
                        "           javax.persistence.spi.PersistenceUnitInfo info, java.util.Map properties) {" +
                        "  return " + HibernatePersistenceHelper.class.getName() + ".createContainerEntityManagerFactoryProxy(" +
                        "      info, properties, _createContainerEntityManagerFactory" + clazz.getSimpleName() + "(info, properties)); " +
                        "}", clazz);
        clazz.addMethod(newMethod);

        oldMethod = clazz.getDeclaredMethod("createEntityManagerFactory");
        oldMethod.setName("_createEntityManagerFactory" + clazz.getSimpleName());

        newMethod = CtNewMethod.make(
                "public javax.persistence.EntityManagerFactory createEntityManagerFactory(" +
                        "           String persistenceUnitName, java.util.Map properties) {" +
                        "  return " + HibernatePersistenceHelper.class.getName() + ".createEntityManagerFactoryProxy(" +
                        "      persistenceUnitName, properties, _createEntityManagerFactory" + clazz.getSimpleName() + "(persistenceUnitName, properties)); " +
                        "}", clazz);
        clazz.addMethod(newMethod);
    }

    /**
     * Remove final flag from SessionFactoryImpl - we need to create a proxy on session factory and cannot
     * use SessionFactory interface, because hibernate makes type cast to impl.
     */
    @Transform(classNameRegexp = "org.hibernate.internal.SessionFactoryImpl")
    public static void removeSessionFactoryImplFinalFlag(CtClass clazz) throws Exception {
        clazz.getClassFile().setAccessFlags(AccessFlag.PUBLIC);
    }

    @Transform(classNameRegexp = "org.hibernate.cfg.Configuration")
    public static void proxySessionFactory(ClassLoader classLoader, ClassPool classPool, CtClass clazz) throws Exception {
        // proceed only if EJB not available by the classloader
        if (checkHibernateEjb(classLoader))
            return;

        LOGGER.debug("Override org.hibernate.cfg.Configuration#buildSessionFactory to create a SessionFactoryProxy proxy.");

        CtClass serviceRegistryClass = classPool.makeClass("org.hibernate.service.ServiceRegistry");
        CtMethod oldMethod = clazz.getDeclaredMethod("buildSessionFactory", new CtClass[]{serviceRegistryClass});
        oldMethod.setName("_buildSessionFactory");

        CtMethod newMethod = CtNewMethod.make(
                "public org.hibernate.SessionFactory buildSessionFactory(org.hibernate.service.ServiceRegistry serviceRegistry) throws org.hibernate.HibernateException {" +
                        "  return " + SessionFactoryProxy.class.getName() + ".getWrapper(this)" +
                        "       .proxy(_buildSessionFactory(serviceRegistry), serviceRegistry); " +
                        "}", clazz);
        clazz.addMethod(newMethod);
    }

    // check if plain Hibernate or EJB mode.
    private static boolean checkHibernateEjb(ClassLoader classLoader) {
        try {
            classLoader.loadClass("org.hibernate.ejb.HibernatePersistence");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
