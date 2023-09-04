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
package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TeacherConfiguration {
    @Bean
    public Teacher1 teacher1() {
        return new Teacher1();
    }

    @Bean(name = "teacher2")
    public Teacher2 teacher2(Student2 student2, @Value("${teacher2.name}") String name) {
        return new Teacher2(name, student2);
    }

    @Bean(name = "teacher22")
    public Teacher2 teacher22(@Value("${teacher2.name}") String name) {
        return new Teacher2(name, null);
    }
}
