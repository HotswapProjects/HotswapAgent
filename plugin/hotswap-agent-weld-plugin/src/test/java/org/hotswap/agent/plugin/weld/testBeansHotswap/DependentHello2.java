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

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.hotswap.agent.plugin.weld.testBeans.HelloProducer1;

/**
 * Basic test bean with Dependent scope.
 */
@Dependent
public class DependentHello2 {
    @Inject
    HelloProducer1 helloProducer;

    public String hello() {
        return "DependentHello2.hello():" + helloProducer.hello();
    }
}
