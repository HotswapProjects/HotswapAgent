package org.hotswap.agent.plugin.spring.xml.factorymethods;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FactoryMethodParentBean1 {

    @Autowired
    private FactoryMethodBean1 factoryMethodBean1;

    public FactoryMethodBean1 getFactoryMethodBean1() {
        return factoryMethodBean1;
    }

    public void setFactoryMethodBean1(FactoryMethodBean1 factoryMethodBean1) {
        this.factoryMethodBean1 = factoryMethodBean1;
    }
}
