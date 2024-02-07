package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InitMethodBeanConfiguration {

    @Bean(initMethod = "init")
    public InitMethodBean1 initMethodBean1() {
        return new InitMethodBean1();
    }

    @Bean(initMethod = "init")
    public InitMethodBean2 initMethodBean2() {
        return new InitMethodBean2();
    }

    @Bean(initMethod = "init")
    public InitMethodBean3 initMethodBean3() {
        return new InitMethodBean3();
    }

    @Bean(initMethod = "init")
    public InitMethodBean4 initMethodBean4() {
        return new InitMethodBean4();
    }
}
