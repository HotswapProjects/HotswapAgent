package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 *
 * @author lysenko
 */
@Dependent
public class HelloServiceDependant{

    @Inject
    HelloProducer helloProducer;

    public String hello() {
        return "Service:" + helloProducer.hello();
    }
}
