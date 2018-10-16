package org.hotswap.agent.plugin.weld.testBeans;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProxyHello1 {
    public String hello() {
        return "ProxyHello1.hello()";
    }
}
