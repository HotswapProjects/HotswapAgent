package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.springframework.beans.factory.annotation.Value;

public class FactoryMethodBean3 {
    @Value("${xml.factory.method.item3.name}")
    private String name = "factoryMethodBean-name3";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
