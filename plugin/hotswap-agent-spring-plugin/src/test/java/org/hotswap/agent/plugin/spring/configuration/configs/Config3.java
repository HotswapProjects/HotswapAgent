package org.hotswap.agent.plugin.spring.configuration.configs;

import org.hotswap.agent.plugin.spring.configuration.beans.BeanA;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config3 {
    @Bean("A")
    public BeanA a() {
        return new BeanA();
    }
}
