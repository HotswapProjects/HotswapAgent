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
package org.hotswap.agent.plugin.hibernate_jakarta.proxy;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hotswap.agent.plugin.hibernate_jakarta.proxy.SessionFactoryProxy;
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
