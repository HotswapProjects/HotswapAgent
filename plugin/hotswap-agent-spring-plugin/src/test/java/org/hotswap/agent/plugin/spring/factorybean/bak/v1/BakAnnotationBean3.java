package org.hotswap.agent.plugin.spring.factorybean.bak.v1;

import org.springframework.stereotype.Component;

@Component
public class BakAnnotationBean3 {
    private String name = "AnnotationBean3-v1";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
