package org.hotswap.agent.plugin.spring.xml.factorymethods;


public class FactoryMethodFactoryBean12 {

    public static FactoryMethodBean1 factoryMethodBean1() throws Exception {
        return new FactoryMethodBean1();
    }

    public static FactoryMethodBean2 factoryMethodBean2(String v) throws Exception {
        return new FactoryMethodBean2(v);
    }

}
