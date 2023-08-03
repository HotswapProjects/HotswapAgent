package org.hotswap.agent.plugin.spring.factorybean.bak.v2;

import org.springframework.stereotype.Component;

@Component
public class V2BakAnnotationBean3 {
    private String name = "AnnotationBean3-v2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
