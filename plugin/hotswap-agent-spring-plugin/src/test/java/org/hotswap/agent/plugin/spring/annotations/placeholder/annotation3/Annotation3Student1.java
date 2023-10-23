package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Annotation3Student1 {

    @Autowired
    public Annotation3Student1(@Value("${annotation3.student.name}") String value) {
        this.name = value;
    }
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
