package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.stereotype.Component;

@Component
public class XmlConstructorBean3 {
    private String name = "xmlConstructorBean3";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
