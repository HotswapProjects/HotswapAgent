package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.stereotype.Component;

@Component
public class XmlFactBean3 {
    private String name = "XmlFactBean3";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
