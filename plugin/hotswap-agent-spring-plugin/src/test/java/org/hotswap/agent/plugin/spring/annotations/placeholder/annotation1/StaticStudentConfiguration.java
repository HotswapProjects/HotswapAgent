package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StaticStudentConfiguration {

    public StaticStudentConfiguration() {
        System.out.println("StaticStudentConfiguration");
    }
    @Bean
    public static StaticStudent1 staticStudent1() {
        return new StaticStudent1();
    }

}
