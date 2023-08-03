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
public class DepTeacherGroupConfiguration {
    @Bean
    public DepTeacherGroup2 depTeacherGroup2(DepTeacher2 depTeacher2) {
        return new DepTeacherGroup2(depTeacher2);
    }

    @Bean
    public DepTeacherGroup1 depTeacherGroup1() {
        return new DepTeacherGroup1();
    }

    @Bean
    public DepTeacherGroup3 depTeacherGroup3(DepTeacher3 depTeacher3) {
        return new DepTeacherGroup3(depTeacher3);
    }

    @Bean
    public DepTeacherGroup4 depTeacherGroup4() {
        return new DepTeacherGroup4();
    }

}
