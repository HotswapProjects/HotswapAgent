package org.hotswap.agent.plugin.spring.annotations.placeholder.annotation1;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan
@PropertySource(value = "classpath:configuration-item.properties")
public class Annotation1Configuration {
}
