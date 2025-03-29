package org.hotswap.agent.plugin.spring.xml.bak.factorymethod;


import org.hotswap.agent.plugin.spring.xml.factorymethods.FactoryMethodBean3;
import org.hotswap.agent.plugin.spring.xml.factorymethods.FactoryMethodBean4;

public class BakFactoryMethodFactoryBean34 {

    public FactoryMethodBean3 factoryMethodBean3() throws Exception {
        return new FactoryMethodBean3();
    }

    public FactoryMethodBean4 factoryMethodBean4(String value) throws Exception {
        return new FactoryMethodBean4("hello:" + value);
    }

    public FactoryMethodBean4 factoryMethodBean44(String value) throws Exception {
        return new FactoryMethodBean4(value);
    }

}
