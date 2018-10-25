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
