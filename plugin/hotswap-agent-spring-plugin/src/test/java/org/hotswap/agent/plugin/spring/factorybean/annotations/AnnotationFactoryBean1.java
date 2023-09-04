package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.FactoryBean;

public class AnnotationFactoryBean1 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean1();
    }

    @Override
    public Class<?> getObjectType() {
        return AnnotationBean1.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
