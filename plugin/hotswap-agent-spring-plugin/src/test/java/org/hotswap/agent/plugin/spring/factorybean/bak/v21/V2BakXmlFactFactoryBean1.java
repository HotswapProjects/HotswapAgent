package org.hotswap.agent.plugin.spring.factorybean.bak.v21;

import org.hotswap.agent.plugin.spring.factorybean.xml.XmlFactBean1;
import org.springframework.beans.factory.FactoryBean;

public class V2BakXmlFactFactoryBean1 implements FactoryBean {
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
