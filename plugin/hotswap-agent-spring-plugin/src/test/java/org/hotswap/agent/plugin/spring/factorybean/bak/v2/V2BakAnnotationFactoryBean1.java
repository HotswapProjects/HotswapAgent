package org.hotswap.agent.plugin.spring.factorybean.bak.v2;

import org.hotswap.agent.plugin.spring.factorybean.annotations.AnnotationBean1;
import org.springframework.beans.factory.FactoryBean;

public class V2BakAnnotationFactoryBean1 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean1();
    }

    @Override
    public Class<?> getObjectType() {
        return AnnotationBean1.class;
    }
}
