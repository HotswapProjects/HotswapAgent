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
package org.hotswap.agent.plugin.spring.wildcardstest.beans.hotswap;


import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanChangedRepository;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BeanServiceImpl2 implements BeanService {
    String name = "Service2";

    @Autowired
    BeanChangedRepository beanRepository;

    public String hello() {
        return beanRepository.hello() + " " + name;
    }

    public String hello2() {
        return beanRepository.hello() + name;
    }

}
