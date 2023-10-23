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

public class DepTeacherGroup2 {

    public DepTeacherGroup2(DepTeacher2 depTeacher2) {
        this.depTeacher2 = depTeacher2;
    }
    private DepTeacher2 depTeacher2;

    public DepTeacher2 getDepTeacher2() {
        return depTeacher2;
    }

    public void setDepTeacher2(DepTeacher2 depTeacher2) {
        this.depTeacher2 = depTeacher2;
    }
}
