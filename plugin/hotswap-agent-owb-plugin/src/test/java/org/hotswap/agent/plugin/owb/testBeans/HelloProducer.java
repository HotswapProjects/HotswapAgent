package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.Dependent;

/**
 * Basic bean
 */
@Dependent
public class HelloProducer {
    public String hello() {
        return "Hello";
    }
}
