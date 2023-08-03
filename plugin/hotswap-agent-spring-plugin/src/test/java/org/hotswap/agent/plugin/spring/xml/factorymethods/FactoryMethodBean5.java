package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class FactoryMethodBean5 {
    @Value("${xml.factory.method.item5.name}")
    private String name = "factoryMethodBean-name5";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
