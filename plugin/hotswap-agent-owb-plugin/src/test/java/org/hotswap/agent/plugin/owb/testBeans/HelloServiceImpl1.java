package org.hotswap.agent.plugin.owb.testBeans;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * Basic service bean
 */
@Singleton
public class HelloServiceImpl1 implements HelloService {
    @Inject
    HelloProducer1 helloProducer;

    public String hello() {
        return "HelloServiceImpl1.hello():" + helloProducer.hello();
    }
}
