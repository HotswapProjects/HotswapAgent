/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.hotswap.agent.plugin.owb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.config.WebBeansFinder;
import org.apache.webbeans.exception.WebBeansConfigurationException;
import org.apache.webbeans.inject.OWBInjector;
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

    public void inject(final Object bean)
    {
        OWBInjector.inject(getBeanManager(), bean, null);
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
