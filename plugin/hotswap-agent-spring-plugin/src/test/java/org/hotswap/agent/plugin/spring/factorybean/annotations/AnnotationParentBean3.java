package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.annotation.Autowired;

public class AnnotationParentBean3 {

    private AnnotationBean3 annotationBean3;

    public AnnotationParentBean3(AnnotationBean3 annotationBean3) {
        this.annotationBean3 = annotationBean3;
    }

    public AnnotationParentBean3() {
    }

    public AnnotationBean3 getAnnotationBean3() {
        return annotationBean3;
    }

}
