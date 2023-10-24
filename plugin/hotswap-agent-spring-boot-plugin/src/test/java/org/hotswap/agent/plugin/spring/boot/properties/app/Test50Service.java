package org.hotswap.agent.plugin.spring.boot.properties.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Test50Service {
    @Value("${properties.l50.l1}")
    private String l1;
    @Value("${properties.l50.l2}")
    private String l2;
    @Value("${properties.l50.l3}")
    private String l3;
    @Value("${properties.l50.l4}")
    private String l4;
    @Value("${properties.l50.l5}")
    private Integer l5;
    @Value("${properties.l50.l11}")
    private String l11;
    @Value("${properties.l50.l12}")
    private String l12;
    @Value("${properties.l50.l10}")
    private String l10;

    public String getL1() {
        return l1;
    }

    public String getL2() {
        return l2;
    }

    public String getL3() {
        return l3;
    }

    public String getL4() {
        return l4;
    }

    public Integer getL5() {
        return l5;
    }

    public String getL11() {
        return l11;
    }

    public String getL12() {
        return l12;
    }

    public String getL10() {
        return l10;
    }
}
