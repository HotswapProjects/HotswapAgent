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
package org.hotswap.agent.plugin.spring.getbean.jdkproxy;

import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdkProxyReloadConfiguration {
    @Bean
    public ReloadGreeterImpl reloadGreeterImpl() {
        return new ReloadGreeterImpl();
    }

    // a JDK dynamic proxy, like Spring AOP creates for a bean with an interface
    @Bean
    public ProxyFactoryBean reloadGreeter(ReloadGreeterImpl reloadGreeterImpl) {
        ProxyFactoryBean factory = new ProxyFactoryBean();
        factory.setTarget(reloadGreeterImpl);
        factory.setInterfaces(ReloadGreeter.class);
        return factory;
    }
}
