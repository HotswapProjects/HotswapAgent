package org.hotswap.agent.plugin.hibernate;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

/**
 * Created by bubnik on 4.11.13.
 */
public class SessionFactoryWrapperTest {

    Mockery context = new Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    final SessionFactory sessionFactory = context.mock(SessionFactory.class);
    final ServiceRegistry serviceRegistry = context.mock(ServiceRegistry.class);
    final Configuration configuration = context.mock(Configuration.class);

    //@Test
    public void testProxy() throws Exception {

        context.checking(new Expectations() {{
            oneOf(sessionFactory).getCurrentSession();
        }});

        SessionFactoryWrapper wrapper = SessionFactoryWrapper.getWrapper(configuration);
        SessionFactory proxy = wrapper.proxy(sessionFactory, serviceRegistry);
        proxy.getCurrentSession();

        context.assertIsSatisfied();
    }
}
