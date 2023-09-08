package org.hotswap.agent.plugin.spring;

public class ReconfigureTestParam {

    public static void configMaxReloadTimes(int times) {
        SpringChangedHub.maxWaitTimes = times;
    }
}
