package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.springframework.beans.factory.annotation.Autowired;

public class FactoryMethodParentBean5 {

    private FactoryMethodBean5 factoryMethodBean5;

    @Autowired
    public FactoryMethodParentBean5(FactoryMethodBean5 factoryMethodBean5) {
        this.factoryMethodBean5 = factoryMethodBean5;
    }

    public FactoryMethodBean5 getFactoryMethodBean5() {
        return factoryMethodBean5;
    }

    public void setFactoryMethodBean5(FactoryMethodBean5 factoryMethodBean5) {
        this.factoryMethodBean5 = factoryMethodBean5;
    }
}
