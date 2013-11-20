package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.PluginManager;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.util.Collections;

/**
 * Created by bubnik on 11.10.13.
 */
public class HibernatePluginTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    final PersistenceUnitManager persistenceUnitManager = context.mock(PersistenceUnitManager.class);
    final PersistenceUnitInfo persistenceUnitInfo = context.mock(PersistenceUnitInfo.class);
    final PersistenceProvider persistenceProvider = context.mock(PersistenceProvider.class);
    final EntityManagerFactory entityManagerFactory = context.mock(EntityManagerFactory.class);
    final PluginManager pluginManager = context.mock(PluginManager.class);

    @Test
    public void testTransform() throws Exception {

        LocalContainerEntityManagerFactoryBeanTest bean = new LocalContainerEntityManagerFactoryBeanTest();
        bean.setPersistenceUnitManager(persistenceUnitManager);
        bean.setPersistenceProvider(persistenceProvider);

        context.checking(new Expectations() {{
            allowing(persistenceUnitManager).obtainDefaultPersistenceUnitInfo();
            will(returnValue(persistenceUnitInfo));
            allowing(persistenceUnitInfo).getPersistenceUnitName();
            will(returnValue("TestPU"));
            allowing(persistenceProvider).createContainerEntityManagerFactory(persistenceUnitInfo, Collections.emptyMap());
            will(returnValue(entityManagerFactory));

        }});

        // execute
        bean.test();

        context.assertIsSatisfied();
    }

    // test class because createNativeEntityManagerFactory is protected
    private static class LocalContainerEntityManagerFactoryBeanTest extends LocalContainerEntityManagerFactoryBean {
        public void test() {
            createNativeEntityManagerFactory();
        }
    }
}
