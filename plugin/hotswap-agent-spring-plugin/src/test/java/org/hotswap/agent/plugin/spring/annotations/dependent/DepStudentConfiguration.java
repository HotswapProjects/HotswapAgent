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
package org.hotswap.agent.plugin.spring.annotations.dependent;

import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1.Student2;
import org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1.Student3;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class DepStudentConfiguration {
    @Bean
    public DepStudent2 depStudent2() {
        return new DepStudent2();
    }

    @Bean
    public DepStudent3 depStudent3() {
        return new DepStudent3();
    }

    @Bean
    public DepStudent4 depStudent4() {
        return new DepStudent4();
    }
}
