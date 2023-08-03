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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DepTeacherConfiguration {
    @Bean(name = "depTeacher2")
    public DepTeacher2 depTeacher2(DepStudent2 depStudent2) {
        return new DepTeacher2(depStudent2);
    }

    @Bean(name = "depTeacher21")
    public DepTeacher2 depTeacher21(DepStudent2 depStudent2) {
        return new DepTeacher2(depStudent2);
    }

    @Bean
    public DepTeacher1 depTeacher1() {
        return new DepTeacher1();
    }

    @Bean
    public DepTeacher4 depTeacher4() {
        return new DepTeacher4();
    }

    @Bean
    public DepTeacher23Mul depTeacher23Mul(DepStudent2 depStudent2, DepStudent3 depStudent3) {
        return new DepTeacher23Mul(depStudent2, depStudent3);
    }
}
