package org.hotswap.agent.plugin.owb.testBeansHotswap;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.hotswap.agent.plugin.owb.testBeans.ChangedHelloProducer;

/**
 * Change BeanHelloProducer2, use @Inject to check that new bean is really created. Test Service that uses this
 * bean that it is indeed recreated with new configuration.
 */
@Dependent
public class HelloProducer2 {
    @Inject
    ChangedHelloProducer changedHello;

    public String hello() {
        return changedHello.hello() + "2";
    }

    public String helloNewMethod() {
        return "Hello from helloNewMethod2";
    }
}
