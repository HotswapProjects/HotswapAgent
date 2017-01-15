package org.hotswap.agent.plugin.owb.testBeans;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * Basic service bean
 */
@Singleton
public class HelloServiceImpl implements HelloService {
    @Inject
    HelloProducer helloProducer;

    public String hello() {
        return "Service:" + helloProducer.hello();
    }
}
