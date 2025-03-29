package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

public class StaticStudent1 {
    @Value("${student.name}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
