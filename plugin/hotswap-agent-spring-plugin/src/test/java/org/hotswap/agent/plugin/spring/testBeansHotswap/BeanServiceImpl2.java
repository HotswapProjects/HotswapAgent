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
package org.hotswap.agent.plugin.spring.testBeansHotswap;

import javax.inject.Inject;

import org.hotswap.agent.plugin.spring.testBeans.BeanChangedRepository;
import org.hotswap.agent.plugin.spring.testBeans.BeanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Basic service bean
 */
@Service
public class BeanServiceImpl2 implements BeanService {
    String name = "Service2";

    @Autowired
    BeanChangedRepository beanChangedRepository;

    @Inject
    BeanChangedRepository beanChangedRepositoryWithInject;

    @Override
    public String hello() {
        if (beanChangedRepository == null) {
            System.out.println("====xxxx:" + this);
        }
        return beanChangedRepository.hello() + " " + name;
    }

    public String helloNewMethod() {
        return "Hello from helloNewMethod " + name;
    }

    @Override
    public String isInjectFieldInjected() {
        return "injectedChanged:" + (beanChangedRepositoryWithInject != null);
    }
}
