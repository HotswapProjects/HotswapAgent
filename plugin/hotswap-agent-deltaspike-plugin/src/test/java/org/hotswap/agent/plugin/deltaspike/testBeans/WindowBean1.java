package org.hotswap.agent.plugin.deltaspike.testBeans;

import java.io.Serializable;

import javax.inject.Inject;

import org.apache.deltaspike.core.api.scope.WindowScoped;

@WindowScoped
public class WindowBean1 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProxyHello1 proxyHello;

    public String hello() {
        return "WindowBean1.hello():" + proxyHello.hello();
    }
}