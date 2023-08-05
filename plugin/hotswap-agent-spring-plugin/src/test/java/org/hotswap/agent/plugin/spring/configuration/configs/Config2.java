package org.hotswap.agent.plugin.spring.configuration.configs;

import org.hotswap.agent.plugin.spring.configuration.beans.BeanA;
import org.hotswap.agent.plugin.spring.configuration.beans.BeanC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config2 {
    @Bean
    public BeanA a() {
        return new BeanA();
    }

    @Bean
    public BeanC c() {
        return new BeanC();
    }
}
