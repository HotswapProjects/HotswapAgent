package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.reload.SpringChangedReloadCommand;
import org.hotswap.agent.plugin.spring.reload.SpringReloadConfig;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class BaseTestUtil {

    public static boolean finishReloading(ConfigurableListableBeanFactory beanFactory, int reloadTimes) {
        return BeanFactoryAssistant.getBeanFactoryAssistant(beanFactory).getReloadTimes() >= reloadTimes
                && SpringChangedReloadCommand.isEmptyTask();
    }

    public static void configMaxReloadTimes() {
        SpringReloadConfig.setDelayMillis(3000);
    }
}
