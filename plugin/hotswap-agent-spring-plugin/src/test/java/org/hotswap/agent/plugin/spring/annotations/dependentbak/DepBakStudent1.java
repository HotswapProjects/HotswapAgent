package org.hotswap.agent.plugin.spring.annotations.dependentbak;

import org.springframework.stereotype.Component;

@Component("depStudent1")
public class DepBakStudent1 {
    private String name = "student1-changed";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
