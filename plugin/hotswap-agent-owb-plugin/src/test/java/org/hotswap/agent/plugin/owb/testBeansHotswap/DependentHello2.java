package org.hotswap.agent.plugin.owb.testBeansHotswap;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.hotswap.agent.plugin.owb.testBeans.HelloProducer;

/**
 * Basic test bean with Dependent scope.
 */
@Dependent
public class DependentHello2 {
    @Inject
    HelloProducer helloProducer;

    public String hello() {
        return "Dependant2:" + helloProducer.hello();
    }
}
