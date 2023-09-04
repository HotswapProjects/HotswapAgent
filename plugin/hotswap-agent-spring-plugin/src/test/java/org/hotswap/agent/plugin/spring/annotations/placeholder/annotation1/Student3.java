package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;

public class Student3 {
    @Value("${student3.name:s3-default}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
