package org.hotswap.agent.plugin.deltaspike.testBeansHotswap;

import java.io.Serializable;

import javax.inject.Inject;

import org.apache.deltaspike.core.api.scope.WindowScoped;
import org.hotswap.agent.plugin.deltaspike.testBeans.ProxyHello1;

@WindowScoped
public class WindowBean2 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProxyHello2 proxyHello; // test inject to changed field type

    @Inject
    private ProxyHello1 proxyHello2; // test inject to new member

    public String hello() {
        return "WindowBean2.hello()" + ":" + proxyHello.hello() + ":" + proxyHello2.hello();
    }
}
