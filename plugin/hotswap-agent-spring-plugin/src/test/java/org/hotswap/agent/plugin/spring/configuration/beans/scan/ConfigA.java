package org.hotswap.agent.plugin.spring.configuration.beans.scan;

import org.hotswap.agent.plugin.spring.configuration.beans.BeanA;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigA {
    @Bean(name = "aaa")
    public BeanA a() {
        return new BeanA();
    }
}
