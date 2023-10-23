package org.hotswap.agent.plugin.spring.xml.bak.factorymethod;


import org.hotswap.agent.plugin.spring.xml.factorymethods.FactoryMethodBean1;
import org.hotswap.agent.plugin.spring.xml.factorymethods.FactoryMethodBean2;

public class BakFactoryMethodFactoryBean12 {

    public static FactoryMethodBean1 factoryMethodBean1() throws Exception {
        return new FactoryMethodBean1();
    }

    public static FactoryMethodBean2 factoryMethodBean2(String v) throws Exception {
        return new FactoryMethodBean2("hello:" + v);
    }

}
