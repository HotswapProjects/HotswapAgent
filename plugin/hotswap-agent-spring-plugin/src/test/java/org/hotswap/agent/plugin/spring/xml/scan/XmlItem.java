package org.hotswap.agent.plugin.spring.xml.scan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

public class XmlItem {
    @Value("${item.name}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
