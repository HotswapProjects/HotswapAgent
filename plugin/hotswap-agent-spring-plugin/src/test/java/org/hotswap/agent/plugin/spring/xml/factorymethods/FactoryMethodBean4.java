package org.hotswap.agent.plugin.spring.xml.factorymethods;

public class FactoryMethodBean4 {
    private String name = "factoryMethodBean-name4";

    public FactoryMethodBean4(String value) {
        this.name = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
