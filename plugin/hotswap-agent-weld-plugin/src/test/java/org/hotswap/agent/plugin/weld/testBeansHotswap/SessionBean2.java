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
package org.hotswap.agent.plugin.weld.testBeansHotswap;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

import org.hotswap.agent.plugin.weld.testBeans.ProxyHello1;

@SessionScoped
public class SessionBean2 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProxyHello2 proxyHello; // test inject to changed field type

    @Inject
    private ProxyHello1 proxyHello2; // test inject to new member

    public String hello() {
        return "SessionBean2.hello()" + ":" + proxyHello.hello() + ":" + proxyHello2.hello();
    }
}
