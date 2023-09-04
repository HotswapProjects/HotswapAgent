package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.FactoryBean;

public class XmlFactoryBean1 implements FactoryBean {

    @Override
    public Object getObject() throws Exception {
        return new XmlFactBean1();
    }

    @Override
    public Class<?> getObjectType() {
        return XmlFactBean1.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
