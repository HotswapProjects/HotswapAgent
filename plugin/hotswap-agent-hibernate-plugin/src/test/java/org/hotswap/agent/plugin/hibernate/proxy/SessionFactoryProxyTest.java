package org.hotswap.agent.plugin.hibernate.proxy;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

/**
 * @author Jiri Bubnik
 */
public class SessionFactoryProxyTest {

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

        SessionFactoryProxy wrapper = SessionFactoryProxy.getWrapper(configuration);
        SessionFactory proxy = wrapper.proxy(sessionFactory, serviceRegistry);
        proxy.getCurrentSession();

        context.assertIsSatisfied();
    }
}
