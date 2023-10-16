package org.hotswap.agent.plugin.spring.reload;

public class SpringReloadConfig {

    public static int reloadDelayMillis = 1600;


    public static void setDelayMillis(int delayMillis) {
        if (delayMillis > 30000) {
            reloadDelayMillis = 30000;
            return;
        }
        SpringReloadConfig.reloadDelayMillis = delayMillis;
    }
}
