package org.hotswap.agent.plugin.spring.xml.scanbak;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("scanBakItem2")
public class ScanBakItem2 {
    @Value("${scan.item.name}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
