package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class XmlFactoryBean4 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new XmlFactBean4();
    }

    @Override
    public Class<?> getObjectType() {
        return XmlFactBean4.class;
    }
}
