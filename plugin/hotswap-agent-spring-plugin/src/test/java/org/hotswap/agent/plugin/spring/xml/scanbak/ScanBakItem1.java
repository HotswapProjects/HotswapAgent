package org.hotswap.agent.plugin.spring.xml.scanbak;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("scanBakItem1")
public class ScanBakItem1 {
    String name;

    public ScanBakItem1(@Value("${scan.item.name}") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
