package org.hotswap.agent.plugin.owb.testBeans;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

@SessionScoped
public class SessionBean1 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProxyHello1 proxyHello;

    public String hello() {
        return "SessionBean1.hello():" + proxyHello.hello();
    }
}
