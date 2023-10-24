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
