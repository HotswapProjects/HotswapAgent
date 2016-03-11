package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.hotswap.agent.plugin.weld.testBeans.ChangedHelloProducer;
import org.hotswap.agent.plugin.weld.testBeans.HelloService;

/**
 * Basic service bean
 */
@Singleton
@Alternative
public class HelloServiceImpl2 implements HelloService {
    String name;

    @Inject
    ChangedHelloProducer helloChanged;

    public String hello() {
        return name + ":" + helloChanged.hello();
    }

    public String helloNewMethod() {
        return "Hello from helloNewMethod";
    }

    public void initName(){
        this.name = "Service2";
    }
}
