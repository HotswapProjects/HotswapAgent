package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class AnnotationFactoryBean4 implements FactoryBean {
    @Override
    public Object getObject() throws Exception {
        return new AnnotationBean4();
    }

    @Override
    public Class<?> getObjectType() {
        return AnnotationBean4.class;
    }
}
