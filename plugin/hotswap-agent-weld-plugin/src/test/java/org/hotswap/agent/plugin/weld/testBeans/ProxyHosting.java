package org.hotswap.agent.plugin.weld.testBeans;

import javax.inject.Inject;

public class ProxyHosting {

    @Inject
    public ProxyHello1 proxy;

    public String hello() {
        return proxy.hello();
    }
}
