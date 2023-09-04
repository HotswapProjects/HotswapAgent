package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class XmlFactoryBean5 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new XmlFactBean5();
    }

    @Override
    public Class<?> getObjectType() {
        return XmlFactBean5.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
