package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.springframework.beans.factory.annotation.Autowired;

public class FactoryMethodParentBean3 {

    private FactoryMethodBean3 factoryMethodBean3;

    @Autowired
    public FactoryMethodParentBean3(FactoryMethodBean3 factoryMethodBean3) {
        this.factoryMethodBean3 = factoryMethodBean3;
    }

    public FactoryMethodBean3 getFactoryMethodBean3() {
        return factoryMethodBean3;
    }
}
