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

import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.hotswap.agent.plugin.weld_jakarta.testBeans.HelloService;

/**
 * Basic service bean
 */
@Singleton
@Alternative
public class HelloServiceImpl2 extends HelloService {
    String name;

    @Inject
    HelloProducer2 helloProducer;

    public String hello() {
        return name + ":" + helloProducer.hello();
    }

    public String helloNewMethod() {
        return "HelloServiceImpl2.helloNewMethod()";
    }

    public void initName() {
        this.name = "HelloServiceImpl2.hello(initialized)";
    }
}
