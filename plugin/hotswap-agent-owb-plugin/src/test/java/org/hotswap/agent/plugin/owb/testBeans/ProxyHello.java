package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProxyHello {
    public String hello() {
        return "ProxyHello:hello";
    }
}
