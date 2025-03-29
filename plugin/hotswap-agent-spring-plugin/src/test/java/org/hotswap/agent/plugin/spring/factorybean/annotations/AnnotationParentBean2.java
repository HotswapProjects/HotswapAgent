package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.annotation.Autowired;

public class AnnotationParentBean2 {

    @Autowired
    private AnnotationBean2 annotationBean2;

    public AnnotationBean2 getAnnotationBean2() {
        return annotationBean2;
    }
    
}
