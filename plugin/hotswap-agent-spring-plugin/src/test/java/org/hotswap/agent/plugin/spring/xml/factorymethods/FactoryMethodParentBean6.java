package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.springframework.beans.factory.annotation.Autowired;

public class FactoryMethodParentBean6 {

    @Autowired
    private FactoryMethodBean6 factoryMethodBean6;

    public FactoryMethodBean6 getFactoryMethodBean6() {
        return factoryMethodBean6;
    }

    public void setFactoryMethodBean6(FactoryMethodBean6 factoryMethodBean6) {
        this.factoryMethodBean6 = factoryMethodBean6;
    }
}
