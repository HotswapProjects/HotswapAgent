package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation4;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Annotation4Student {
    @Value("${annotation4.student.name}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
