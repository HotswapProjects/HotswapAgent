package org.hotswap.agent.plugin.spring.annotations.dependent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("depStudent1")
public class DepStudent1 {
    private String name = "student1";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
