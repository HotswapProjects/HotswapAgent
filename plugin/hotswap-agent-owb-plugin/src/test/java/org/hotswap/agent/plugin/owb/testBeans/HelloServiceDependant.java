package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 *
 * @author lysenko
 */
@Dependent
public class HelloServiceDependant {

    @Inject
    HelloProducer1 helloProducer;

    public String hello() {
        return "HelloServiceDependant.hello():" + helloProducer.hello();
    }
}
