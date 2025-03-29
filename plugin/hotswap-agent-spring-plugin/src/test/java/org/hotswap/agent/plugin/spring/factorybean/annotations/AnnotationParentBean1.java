package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AnnotationParentBean1 {

    @Autowired
    private AnnotationBean1 annotationBean1;

    public AnnotationBean1 getAnnotationBean1() {
        return annotationBean1;
    }

}
