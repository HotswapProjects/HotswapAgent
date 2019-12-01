package org.hotswap.agent.plugin.deltaspike.transformer;

import java.util.List;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;

import org.hotswap.agent.plugin.deltaspike.DeltaSpikePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

public class HaAfteBeanDiscovery {

    public void $ha$$afterBeanDiscovery(@Observes AfterBeanDiscovery before) {
        PluginManagerInvoker.callPluginMethod(DeltaSpikePlugin.class, getClass().getClassLoader(),
                "registerRepositoryClasses", new Class[] { List.class }, new Object[] { ReflectionHelper.get(this, "repositoryClasses") });
    }

}
