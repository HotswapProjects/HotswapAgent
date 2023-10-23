package org.hotswap.agent.plugin.spring.xml.factorymethods;


public class FactoryMethodFactoryBeanMix {

    public FactoryMethodBean6 factoryMethodBean6(String name) throws Exception {
        return new FactoryMethodBean6(name);
    }

    public static FactoryMethodBean5 factoryMethodBean5() throws Exception {
        return new FactoryMethodBean5();
    }


}
