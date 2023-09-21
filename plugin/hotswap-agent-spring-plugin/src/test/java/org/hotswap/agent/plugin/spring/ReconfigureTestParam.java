package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.plugin.spring.reload.SpringReloadConfig;

public class ReconfigureTestParam {

    public static void configMaxReloadTimes() {
        SpringReloadConfig.setDelayMillis(3000);
    }
}
