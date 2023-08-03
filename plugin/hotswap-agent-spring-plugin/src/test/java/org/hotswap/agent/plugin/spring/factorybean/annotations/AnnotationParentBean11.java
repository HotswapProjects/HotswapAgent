package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AnnotationParentBean11 {

    public AnnotationParentBean11(AnnotationBean1 annotationBean1) {
        this.annotationBean1 = annotationBean1;
    }

    private AnnotationBean1 annotationBean1;

    public AnnotationBean1 getAnnotationBean1() {
        return annotationBean1;
    }

}
