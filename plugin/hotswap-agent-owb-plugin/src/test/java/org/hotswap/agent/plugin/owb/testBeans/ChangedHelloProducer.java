package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.Dependent;

/**
 * Change to this to check reinitialization after hotswap.
 */
@Dependent
public class ChangedHelloProducer {
    public String hello() {
        return "ChangedHello";
    }
}
