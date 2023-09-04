package org.hotswap.agent.plugin.spring.xml.bak.constructor.v1;

import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean2;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class BakConstructorFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new XmlConstructorBean2("ConstructorBean2-v2");
    }

    @Override
    public Class<?> getObjectType() {
        return XmlConstructorBean2.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
