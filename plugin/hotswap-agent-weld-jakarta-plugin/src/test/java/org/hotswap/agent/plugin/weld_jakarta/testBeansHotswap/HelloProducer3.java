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
package org.hotswap.agent.plugin.weld_jakarta.testBeansHotswap;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

/**
 * Change BeanHelloProducer2, use @Inject to check that new bean is really created. Test Service that uses this
 * bean that it is indeed recreated with new configuration.
 */
@Dependent
public class HelloProducer3 {

    @Inject
    HelloProducer2 changedHello;

    public String hello() {
        return "HelloProducer3.hello():" + changedHello.hello();
    }

    public String helloNewMethod() {
        return "HelloProducer3.helloNewMethod()";
    }
}
