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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DepTeacherGroup3 {

    @Autowired
    public DepTeacherGroup3(DepTeacher3 depTeacher3) {
        this.depTeacher3 = depTeacher3;
    }


    private DepTeacher3 depTeacher3;

    public DepTeacher3 getDepTeacher3() {
        return depTeacher3;
    }

    public void setDepTeacher3(DepTeacher3 depTeacher3) {
        this.depTeacher3 = depTeacher3;
    }
}
