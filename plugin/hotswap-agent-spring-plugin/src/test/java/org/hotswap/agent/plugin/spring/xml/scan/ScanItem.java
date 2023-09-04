package org.hotswap.agent.plugin.spring.xml.scan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScanItem {
    String name;

    @Autowired
    public ScanItem(@Value("${scan.item.name}") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
