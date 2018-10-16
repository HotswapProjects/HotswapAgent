package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.hotswap.agent.plugin.weld.testBeans.HelloProducer1;

/**
 * Basic test bean with Dependent scope.
 */
@Dependent
public class DependentHello2 {
    @Inject
    HelloProducer1 helloProducer;

    public String hello() {
        return "DependentHello2.hello():" + helloProducer.hello();
    }
}
