package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class XmlConstructorFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new XmlConstructorBean2("xmlConstructorBean2-v1");
    }

    @Override
    public Class<?> getObjectType() {
        return XmlConstructorBean2.class;
    }
}
