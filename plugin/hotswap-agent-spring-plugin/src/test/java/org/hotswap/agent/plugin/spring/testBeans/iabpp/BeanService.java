package org.hotswap.agent.plugin.spring.testBeans.iabpp;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn(value = {"beanServiceImplNoAspect"})
public class BeanService {
    BeanServiceNoAspect beanService;

    public String hello() {
        return beanService.hello();
    }
}