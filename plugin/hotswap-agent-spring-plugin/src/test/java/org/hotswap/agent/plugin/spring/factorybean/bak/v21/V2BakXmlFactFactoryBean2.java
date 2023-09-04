package org.hotswap.agent.plugin.spring.factorybean.bak.v21;

import org.hotswap.agent.plugin.spring.factorybean.annotations.AnnotationBean2;
import org.hotswap.agent.plugin.spring.factorybean.xml.XmlFactBean2;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class V2BakXmlFactFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new XmlFactBean2("XmlFactBean2-v3");
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
