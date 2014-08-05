package org.hotswap.agent.testData;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.OnClassLoadEvent;

/**
 * Created by bubnik on 11.10.13.
 */
@Plugin(name = "Hibernate plugin", testedVersions = {"1.0"})
public class SimplePlugin {

    @Init
    public void initPlugin() {
    }

    @Init
    public void initPlugin(PluginManager manager) {
    }


    @OnClassLoadEvent(classNameRegexp = "org.hotswap.example.type")
    public void transform() {

    }

    // used by PluginManagerInvokerTest to dynamically call this method.
    public void callPluginMethod(Boolean val) {
    }
}
