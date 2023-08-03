package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class AnnotationFactoryBean5 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean5();
    }

    @Override
    public Class<?> getObjectType() {
        return AnnotationBean5.class;
    }
}
