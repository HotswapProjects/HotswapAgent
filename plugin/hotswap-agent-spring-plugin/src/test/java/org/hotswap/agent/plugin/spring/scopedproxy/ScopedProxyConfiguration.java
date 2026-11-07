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
package org.hotswap.agent.plugin.spring.scopedproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

/**
 * Like @SessionScope, with a thread scope instead of the session. Without @Qualifier: with it, the context does not
 * start with the agent on Spring 5.3, see setCacheBeanMetadata(false) in SpringPlugin and #603.
 */
@Configuration
public class ScopedProxyConfiguration {
    @Bean
    @Scope(scopeName = "thread", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public WebCls webCls() {
        WebCls cls = new WebCls();
        cls.setWeb("aaaa");
        return cls;
    }

    @Bean
    public String plainBean() {
        return "plain-a";
    }
}
