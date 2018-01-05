package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 * Change BeanHelloProducer2, use @Inject to check that new bean is really created. Test Service that uses this
 * bean that it is indeed recreated with new configuration.
 */
@Dependent
public class HelloProducer3 {

    @Inject
    HelloProducer2 changedHello;

    public String hello() {
        return "HelloProducer3.hello():" + changedHello.hello();
    }

    public String helloNewMethod() {
        return "HelloProducer3.helloNewMethod()";
    }
}
