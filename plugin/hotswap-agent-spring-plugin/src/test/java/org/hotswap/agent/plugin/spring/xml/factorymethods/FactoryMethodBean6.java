package org.hotswap.agent.plugin.spring.xml.factorymethods;

public class FactoryMethodBean6 {
    private String name = "factoryMethodBean-name6";

    public FactoryMethodBean6(String value) {
        this.name = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName2() {
        return null;
    }
}
