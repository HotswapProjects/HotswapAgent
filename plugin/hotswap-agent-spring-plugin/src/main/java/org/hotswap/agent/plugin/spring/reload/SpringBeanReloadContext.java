/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.reload;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class SpringBeanReloadContext {
  private static AgentLogger LOGGER = AgentLogger.getLogger(SpringBeanReload.class);

  public Set<Class<?>> classes = new HashSet<>();
  public Set<URL> properties = new HashSet<>();
  public Set<URL> yamls = new HashSet<>();
  public Set<URL> xmls = new HashSet<>();
  public Set<BeanDefinitionHolder> newScanBeanDefinitions = new HashSet<>();
  public Set<Class<?>> newEntities = new HashSet<>();
  public Set<Class<?>> newRepositories = new HashSet<>();
  public Set<String> changedBeanNames = new HashSet<>();

  public Set<String> processedBeans = new HashSet<>();
  public Set<String> destroyClasses = new HashSet<>();
  public Set<String> beansToProcess = new HashSet<>();

  public boolean hasChange(DefaultListableBeanFactory beanFactory) {
    if (properties.isEmpty()
        && classes.isEmpty()
        && xmls.isEmpty()
        && newScanBeanDefinitions.isEmpty()
        && yamls.isEmpty()
        && changedBeanNames.isEmpty()
        && newEntities.isEmpty()) {
      LOGGER.info("no change, ignore reloading '{}'", ObjectUtils.identityToString(beanFactory));
      return false;
    }
    LOGGER.trace("has change, start reloading '{}', {}", ObjectUtils.identityToString(beanFactory), this);
    return true;
  }
}
