package org.hotswap.agent.plugin.spring.xml.bak.constructor.v1;

import org.springframework.stereotype.Component;

@Component
public class BakConstructorBean3 {
    private String name = "ConstructorBean3-v1";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
