/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.boot;

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
