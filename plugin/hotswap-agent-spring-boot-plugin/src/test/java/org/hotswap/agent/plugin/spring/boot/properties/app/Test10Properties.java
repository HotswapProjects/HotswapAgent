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
package org.hotswap.agent.plugin.spring.boot.properties.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "properties.l10")
public class Test10Properties {
    private String l1;
    private String l2;
    private String l3;
    private String l4;
    private Integer l5;
    private String l10;
    private String l11;
    private String l12;

    public String getL1() {
        return l1;
    }

    public void setL1(String l1) {
        this.l1 = l1;
    }

    public String getL2() {
        return l2;
    }

    public void setL2(String l2) {
        this.l2 = l2;
    }

    public String getL3() {
        return l3;
    }

    public void setL3(String l3) {
        this.l3 = l3;
    }

    public String getL4() {
        return l4;
    }

    public void setL4(String l4) {
        this.l4 = l4;
    }

    public Integer getL5() {
        return l5;
    }

    public void setL5(Integer l5) {
        this.l5 = l5;
    }

    public String getL11() {
        return l11;
    }

    public void setL11(String l11) {
        this.l11 = l11;
    }

    public String getL12() {
        return l12;
    }

    public void setL12(String l12) {
        this.l12 = l12;
    }

    public String getL10() {
        return l10;
    }

    public void setL10(String l10) {
        this.l10 = l10;
    }
}
