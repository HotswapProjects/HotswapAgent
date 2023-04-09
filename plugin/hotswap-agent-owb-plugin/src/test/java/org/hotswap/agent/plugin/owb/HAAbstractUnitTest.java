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
package org.hotswap.agent.plugin.owb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.config.WebBeansFinder;
import org.apache.webbeans.exception.WebBeansConfigurationException;
import org.apache.webbeans.spi.ContainerLifecycle;
import org.apache.webbeans.spi.ContextsService;
import org.apache.webbeans.util.WebBeansUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;


public abstract class HAAbstractUnitTest
{
    private HAOpenWebBeansTestLifeCycle testLifecycle;
    private WebBeansContext webBeansContext;

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
        WebBeansFinder.clearInstances(WebBeansUtil.getCurrentClassLoader());
        //Creates a new container
        testLifecycle = new HAOpenWebBeansTestLifeCycle();

        webBeansContext = WebBeansContext.getInstance();

        //Start application
        try
        {
            testLifecycle.startApplication(null);
        }
        catch (Exception e)
        {
            throw new WebBeansConfigurationException(e);
        }
    }

    protected ContainerLifecycle getLifecycle()
    {
        return testLifecycle;
    }

    protected void shutDownContainer()
    {
        //Shutdown application
        if(this.testLifecycle != null)
        {
            this.testLifecycle.stopApplication(null);
            this.testLifecycle = null;
            cleanup();
        }
    }

    protected WebBeansContext getWebBeansContext()
    {
        return this.webBeansContext;
    }

    protected BeanManager getBeanManager()
    {
        return this.webBeansContext.getBeanManagerImpl();
    }

    @SuppressWarnings("unchecked")
    protected <T> Bean<T> getBean(Class<T> type, Annotation... qualifiers)
    {
        Set beans = getBeanManager().getBeans(type, qualifiers);
        return (Bean<T>) getBeanManager().resolve(beans);
    }

    protected <T> T getInstance(Class<T> type, Annotation... qualifiers)
    {
        return getInstance((Type) type, qualifiers);
    }

    protected <T> T getInstance(Type type, Annotation... qualifiers)
    {
        Set<Bean<?>> beans = getBeanManager().getBeans(type, qualifiers);
        Assert.assertNotNull(beans);

        Bean<?> bean = getBeanManager().resolve(beans);
        Assert.assertNotNull("Bean with type " + type + " could not be found!", bean);

        return (T) getBeanManager().getReference(bean, type, getBeanManager().createCreationalContext(bean));
    }

    protected void restartContext(Class<? extends Annotation> scopeType)
    {
        ContextsService contextsService = webBeansContext.getContextsService();
        contextsService.endContext(scopeType, null);
        contextsService.startContext(scopeType, null);
    }

    protected void startContext(Class<? extends Annotation> scopeType)
    {
        webBeansContext.getContextsService().startContext(scopeType, null);
    }

    protected void endContext(Class<? extends Annotation> scopeType)
    {
        webBeansContext.getContextsService().endContext(scopeType, null);
    }
}
