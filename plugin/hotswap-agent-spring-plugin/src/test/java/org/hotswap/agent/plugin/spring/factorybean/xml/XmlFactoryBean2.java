package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class XmlFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new XmlFactBean2("XmlFactBean2-v1");
    }

    @Override
    public Class<?> getObjectType() {
        return XmlFactBean2.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
