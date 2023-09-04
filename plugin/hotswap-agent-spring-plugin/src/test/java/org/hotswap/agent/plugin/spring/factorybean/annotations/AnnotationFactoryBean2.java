package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class AnnotationFactoryBean2 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean2("AnnotationBean2-v1");
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
