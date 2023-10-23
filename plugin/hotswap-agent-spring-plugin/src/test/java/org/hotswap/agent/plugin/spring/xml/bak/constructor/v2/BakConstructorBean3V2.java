package org.hotswap.agent.plugin.spring.xml.bak.constructor.v2;

import org.springframework.stereotype.Component;

@Component
public class BakConstructorBean3V2 {
    private String name = "ConstructorBean3-v2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
