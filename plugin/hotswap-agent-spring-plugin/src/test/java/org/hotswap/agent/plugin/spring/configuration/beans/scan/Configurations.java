package org.hotswap.agent.plugin.spring.configuration.beans.scan;


import org.hotswap.agent.plugin.spring.configuration.beans.BeanA;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class Configurations {

    @Bean(name = "bccc")
    public BeanA a() {
        return new BeanA();
    }
}
