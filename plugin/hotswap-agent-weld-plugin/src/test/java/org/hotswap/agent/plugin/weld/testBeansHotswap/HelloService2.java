package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.hotswap.agent.plugin.weld.testBeans.ChangedHelloProducer;

/**
 * Basic service bean
 */
@Singleton
public class HelloService2 {
    String name = "Service2";

    @Inject
    ChangedHelloProducer changedHello;

    public String hello() {
        return name + ":" + changedHello.hello();
    }
}
