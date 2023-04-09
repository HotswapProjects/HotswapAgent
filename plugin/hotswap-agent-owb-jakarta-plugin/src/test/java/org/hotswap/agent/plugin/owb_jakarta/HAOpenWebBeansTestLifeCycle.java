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
package org.hotswap.agent.plugin.owb_jakarta;

import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Singleton;

import jakarta.servlet.http.HttpSession;
import org.apache.webbeans.annotation.InitializedLiteral;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.se.DefaultScannerService;
import org.apache.webbeans.el.ELContextStore;
import org.apache.webbeans.lifecycle.AbstractLifeCycle;
import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.spi.ContextsService;
import org.apache.webbeans.web.context.WebContextsService;
import org.apache.webbeans.web.lifecycle.test.MockHttpSession;


public class HAOpenWebBeansTestLifeCycle extends AbstractLifeCycle
{
    // private MockServletContextEvent servletContextEvent;
    HttpSession mockHttpSession = new MockHttpSession();

    public HAOpenWebBeansTestLifeCycle()
    {
        super(new Properties()
        {{
            setProperty(ContextsService.class.getName(), WebContextsService.class.getName());
        }});
        this.logger = WebBeansLoggerFacade.getLogger(HAOpenWebBeansTestLifeCycle.class);
    }

    @Override
    public void beforeInitApplication(Properties properties)
    {
        this.scannerService = new DefaultScannerService();
    }

    public void beforeStartApplication(Object object)
    {
        WebBeansContext webBeansContext = getWebBeansContext();
        ContextsService contextsService = webBeansContext.getContextsService();
        contextsService.startContext(Singleton.class, null);
        contextsService.startContext(ApplicationScoped.class, null);
    }

    protected void afterStartApplication(Object startupObject)
    {
        this.webBeansContext.getBeanManagerImpl().fireContextLifecyleEvent(new Object(), InitializedLiteral.INSTANCE_APPLICATION_SCOPED);

        WebBeansContext webBeansContext = getWebBeansContext();
        ContextsService contextsService = webBeansContext.getContextsService();

        contextsService.startContext(RequestScoped.class, null);
        contextsService.startContext(SessionScoped.class, mockHttpSession);
    }

    public void beforeStopApplication(Object endObject)
    {
        WebBeansContext webBeansContext = getWebBeansContext();
        ContextsService contextsService = webBeansContext.getContextsService();
        contextsService.endContext(Singleton.class, null);
        contextsService.endContext(ApplicationScoped.class, null);
        contextsService.endContext(RequestScoped.class, null);
        contextsService.endContext(SessionScoped.class, mockHttpSession);

        ELContextStore elStore = ELContextStore.getInstance(false);
        if (elStore == null)
        {
            return;
        }
        elStore.destroyELContextStore();
    }
}
