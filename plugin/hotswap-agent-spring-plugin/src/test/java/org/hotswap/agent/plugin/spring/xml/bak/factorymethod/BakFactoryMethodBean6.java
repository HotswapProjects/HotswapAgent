package org.hotswap.agent.plugin.spring.xml.bak.factorymethod;

import org.springframework.beans.factory.annotation.Value;

public class BakFactoryMethodBean6 {
    private String name = "factoryMethodBean-name6";

    @Value("${xml.factory.method.item6.name2}")
    private String name2;

    public BakFactoryMethodBean6(String value) {
        this.name = "hello:" + value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName2() {
        return name2;
    }
}
