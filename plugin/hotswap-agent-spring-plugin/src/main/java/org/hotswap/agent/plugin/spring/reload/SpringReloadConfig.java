package org.hotswap.agent.plugin.spring.reload;

public class SpringReloadConfig {

    static int reloadDelayMillis = 1000;
    static int reloadTimeout = 30000;


    public static void setDelayMillis(int delayMillis) {
        if (delayMillis > 30000) {
            reloadDelayMillis = 30000;
            return;
        }
        SpringReloadConfig.reloadDelayMillis = delayMillis;
    }
}
