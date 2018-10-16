package org.hotswap.agent.plugin.owb.testBeansHotswap;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Basic service bean
 */
@Singleton
public class HelloService2 {
    String name = "HelloService2.hello";

    @Inject
    HelloProducer2 helloProducer;

    public String hello() {
        return name + ":" + helloProducer.hello();
    }
}
