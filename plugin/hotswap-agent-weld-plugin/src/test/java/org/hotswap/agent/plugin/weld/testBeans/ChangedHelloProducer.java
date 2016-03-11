package org.hotswap.agent.plugin.weld.testBeans;


/**
 * Change to this to check reinitialization after hotswap.
 */
public class ChangedHelloProducer {
    public String hello() {
        return "ChangedHello";
    }
}
