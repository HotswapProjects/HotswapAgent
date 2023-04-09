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
package org.hotswap.agent.plugin.deltaspike;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.apache.deltaspike.cdise.api.CdiContainer;
import org.apache.deltaspike.cdise.api.CdiContainerLoader;
import org.apache.deltaspike.cdise.api.ContextControl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public abstract class HAAbstractUnitTest
{
    protected HAAbstractUnitTest()
    {
    }

    @Before
    public void cleanup()
    {
    }

    /**
     * always shut down the container after each test.
     */
    @After
    public void shutdown()
    {
        shutDownContainer();
    }

    protected void startContainer()
    {
        CdiContainer cdiContainer = CdiContainerLoader.getCdiContainer();
        cdiContainer.boot();
        ContextControl contextControl = cdiContainer.getContextControl();
        contextControl.startContext(ApplicationScoped.class);
        contextControl.startContext(SessionScoped.class);
        contextControl.startContext(RequestScoped.class);
    }

    protected void shutDownContainer()
    {
        CdiContainerLoader.getCdiContainer().shutdown();
    }

    @SuppressWarnings("unchecked")
    protected <T> Bean<T> getBean(Class<T> type, Annotation... qualifiers)
    {
        BeanManager beanManager = CDI.current().getBeanManager();
        Set beans = beanManager.getBeans(type, qualifiers);
        return (Bean<T>) beanManager.resolve(beans);
    }

    protected <T> T getInstance(Class<T> type, Annotation... qualifiers)
    {
        return getInstance((Type) type, qualifiers);
    }

    protected <T> T getInstance(Type type, Annotation... qualifiers)
    {
        BeanManager beanManager = CDI.current().getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(type, qualifiers);
        Assert.assertNotNull(beans);

        Bean<?> bean = beanManager.resolve(beans);
        Assert.assertNotNull("Bean with type " + type + " could not be found!", bean);

        return (T) beanManager.getReference(bean, type, beanManager.createCreationalContext(bean));
    }
}
