package org.hotswap.agent.plugin.owb;

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.inject.Singleton;

import org.apache.webbeans.annotation.InitializedLiteral;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.se.DefaultScannerService;
import org.apache.webbeans.el.ELContextStore;
import org.apache.webbeans.lifecycle.AbstractLifeCycle;
import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.spi.ContextsService;
import org.apache.webbeans.web.context.WebContextsService;

public class HAOpenWebBeansTestLifeCycle extends AbstractLifeCycle
{
    // private MockServletContextEvent servletContextEvent;
    // private MockHttpSession mockHttpSession;

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
        contextsService.startContext(SessionScoped.class, null);
    }

    public void beforeStopApplication(Object endObject)
    {
        WebBeansContext webBeansContext = getWebBeansContext();
        ContextsService contextsService = webBeansContext.getContextsService();
        contextsService.endContext(Singleton.class, null);
        contextsService.endContext(ApplicationScoped.class, null);
        contextsService.endContext(RequestScoped.class, null);
        contextsService.endContext(SessionScoped.class, null);

        ELContextStore elStore = ELContextStore.getInstance(false);
        if (elStore == null)
        {
            return;
        }
        elStore.destroyELContextStore();
    }
}
