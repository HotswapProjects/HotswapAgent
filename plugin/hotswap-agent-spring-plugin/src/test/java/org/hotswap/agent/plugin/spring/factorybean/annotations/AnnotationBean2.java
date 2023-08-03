package org.hotswap.agent.plugin.spring.factorybean.annotations;

public class AnnotationBean2 {

    public AnnotationBean2(String value) {
        this.name = value;
    }
    private String name = "AnnotationBean2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
