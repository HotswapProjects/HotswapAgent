package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1;

import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@Configuration
@ComponentScan
@PropertySource(value = "classpath:annotation-configuration/configuration-item.properties")
public class Annotation1Configuration {
    /**
     * prior to spring 4.1, this bean is required to resolve ${...} placeholders within @Value annotations and <context:property-placeholder/> configuration.
     * @return
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
