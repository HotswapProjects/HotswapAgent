package org.hotswap.agent.plugin.spring.xml.factorymethods;

public class FactoryMethodBean2 {

    public FactoryMethodBean2(String value) {
        this.name = value;
    }
    private String name = "factoryMethodBean-name2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
