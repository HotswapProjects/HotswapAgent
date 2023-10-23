package org.hotswap.agent.plugin.spring.factorybean.bak.v11;

import org.hotswap.agent.plugin.spring.factorybean.xml.XmlFactBean2;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BakXmlFactFactoryBean2 implements FactoryBean {
    @Value("${xml.beanfactory.item2.name}")
    private String value;

    @Override
    public Object getObject() throws Exception {
        return new XmlFactBean2(value);
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
