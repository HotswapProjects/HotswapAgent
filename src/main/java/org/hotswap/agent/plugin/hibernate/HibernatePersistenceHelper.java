package org.hotswap.agent.plugin.hibernate;

import org.hibernate.Version;
import org.hotswap.agent.util.PluginManagerInvoker;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitInfo;
import java.util.Map;

/**
 * @author Jiri Bubnik
 */
public class HibernatePersistenceHelper {
    /**
     * @param info       persistent unit definition
     * @param properties properties to create entity manager factory
     * @param original   entity manager factory
     * @return
     */
    public static EntityManagerFactory createContainerEntityManagerFactoryProxy(PersistenceUnitInfo info, Map properties,
                                                                                EntityManagerFactory original) {
        EntityManagerFactoryWrapper wrapper = EntityManagerFactoryWrapper.getWrapper(info.getPersistenceUnitName());
        EntityManagerFactory proxy = wrapper.proxy(original, info, properties);

        ClassLoader appClassLoader = original.getClass().getClassLoader();

        String version = Version.getVersionString();

        PluginManagerInvoker.callInitializePlugin(HibernatePlugin.class, appClassLoader);
        PluginManagerInvoker.callPluginMethod(HibernatePlugin.class, appClassLoader,
                "hibernateInitialized",
                new Class[]{String.class, Boolean.class},
                new Object[]{version, true});
        return proxy;
    }
}
