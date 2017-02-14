package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProxyHello2 {
    public String hello() {
        return "ProxyHello2:hello";
    }
    public String hello2() {
        return "ProxyHello2:hello2";
    }
}
