package org.hotswap.agent.plugin.spring.annotations.dependent;

import org.springframework.beans.factory.annotation.Value;

public class DepStudent2 {
    private String name = "student2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
