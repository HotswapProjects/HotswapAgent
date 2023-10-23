package org.hotswap.agent.plugin.spring.factorybean.bak.v1;

import org.hotswap.agent.plugin.spring.factorybean.annotations.AnnotationBean2;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class BakAnnotationFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean2("AnnotationBean2-v2");
    }

    @Override
    public Class<?> getObjectType() {
        return AnnotationBean2.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
