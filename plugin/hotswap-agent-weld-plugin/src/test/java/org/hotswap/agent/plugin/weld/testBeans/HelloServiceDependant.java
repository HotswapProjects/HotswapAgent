package org.hotswap.agent.plugin.weld.testBeans;

import javax.inject.Inject;

/**
 *
 * @author lysenko
 */
public class HelloServiceDependant{

    @Inject
    HelloProducer helloProducer;

    public String hello() {
        return "Service:" + helloProducer.hello();
    }
}
