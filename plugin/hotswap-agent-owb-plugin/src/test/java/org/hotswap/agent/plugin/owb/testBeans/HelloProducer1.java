package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.Dependent;

/**
 * Basic dependant bean
 */
@Dependent
public class HelloProducer1 {
    public String hello() {
        return "HelloProducer1.hello()";
    }
}
