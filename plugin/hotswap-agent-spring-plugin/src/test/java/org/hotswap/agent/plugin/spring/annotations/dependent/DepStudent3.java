package org.hotswap.agent.plugin.spring.annotations.dependent;

import org.springframework.beans.factory.annotation.Value;

public class DepStudent3 {
    private String name = "student3";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
