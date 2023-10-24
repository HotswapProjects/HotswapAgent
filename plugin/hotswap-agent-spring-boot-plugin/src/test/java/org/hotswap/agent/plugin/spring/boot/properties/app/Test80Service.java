package org.hotswap.agent.plugin.spring.boot.properties.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
public class Test80Service {
    @Value("${properties.l80.l1}")
    private String l1;
    @Value("${properties.l80.l2}")
    private String l2;
    @Value("${properties.l80.l3}")
    private String l3;
    @Value("${properties.l80.l4}")
    private String l4;
    @Value("${properties.l80.l5}")
    private String l5;

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

    public String getL5() {
        return l5;
    }

    public void setL5(String l5) {
        this.l5 = l5;
    }
}
