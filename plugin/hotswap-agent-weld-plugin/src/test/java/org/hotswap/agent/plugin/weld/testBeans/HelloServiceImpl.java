package org.hotswap.agent.plugin.weld.testBeans;

import javax.inject.Inject;


/**
 * Basic service bean
 */
public class HelloServiceImpl implements HelloService {
    @Inject
    HelloProducer helloProducer;

    public String hello() {
        return "Service:" + helloProducer.hello();
    }
}
