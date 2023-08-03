package org.hotswap.agent.plugin.spring.xml.factorymethods;

public class FactoryMethodParentBean2 {

    private FactoryMethodBean2 factoryMethodBean2;

    public FactoryMethodParentBean2(FactoryMethodBean2 factoryMethodBean2) {
        this.factoryMethodBean2 = factoryMethodBean2;
    }

    public FactoryMethodBean2 getFactoryMethodBean2() {
        return factoryMethodBean2;
    }

    public void setFactoryMethodBean2(FactoryMethodBean2 factoryMethodBean2) {
        this.factoryMethodBean2 = factoryMethodBean2;
    }
}
