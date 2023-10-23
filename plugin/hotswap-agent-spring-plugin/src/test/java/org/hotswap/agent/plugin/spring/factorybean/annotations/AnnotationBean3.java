package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.stereotype.Component;

@Component
public class AnnotationBean3 {
    private String name = "AnnotationBean3";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
