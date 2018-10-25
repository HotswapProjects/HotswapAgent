package org.hotswap.agent.plugin.owb.testBeansHotswap;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

import org.hotswap.agent.plugin.owb.testBeans.ProxyHello1;

@SessionScoped
public class SessionBean2 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProxyHello2 proxyHello; // test inject to changed field type

    @Inject
    private ProxyHello1 proxyHello2; // test inject to new member

    public String hello() {
        return "SessionBean2.hello()" + ":" + proxyHello.hello() + ":" + proxyHello2.hello();
    }
}
