package org.hotswap.agent.plugin.owb.testBeansHotswap;

import javax.enterprise.context.Dependent;

/**
 * Basic dependant bean
 */
@Dependent
public class HelloProducer2 {
    public String hello() {
        return "HelloProducer2.hello()";
    }
}
