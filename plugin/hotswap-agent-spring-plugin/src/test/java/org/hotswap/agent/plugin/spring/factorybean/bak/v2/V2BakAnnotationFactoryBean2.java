package org.hotswap.agent.plugin.spring.factorybean.bak.v2;

import org.hotswap.agent.plugin.spring.factorybean.annotations.AnnotationBean2;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class V2BakAnnotationFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean2("AnnotationBean2-v3");
    }

    @Override
    public Class<?> getObjectType() {
        return AnnotationBean2.class;
    }
}
