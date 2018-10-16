package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.hotswap.agent.plugin.weld.testBeans.HelloService;

/**
 * Basic service bean
 */
@Singleton
@Alternative
public class HelloServiceImpl2 implements HelloService {
    String name;

    @Inject
    HelloProducer2 helloProducer;

    public String hello() {
        return name + ":" + helloProducer.hello();
    }

    public String helloNewMethod() {
        return "HelloServiceImpl2.helloNewMethod()";
    }

    public void initName() {
        this.name = "HelloServiceImpl2.hello(initialized)";
    }
}
