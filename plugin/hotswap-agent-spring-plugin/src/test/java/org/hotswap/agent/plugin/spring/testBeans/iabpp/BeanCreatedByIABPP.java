package org.hotswap.agent.plugin.spring.testBeans.iabpp;

import org.hotswap.agent.plugin.spring.testBeans.BeanServiceNoAspect;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn(value = {"beanServiceImplNoAspect"})
public class BeanCreatedByIABPP {
    BeanServiceNoAspect beanService;

    public String helloWithoutAspec() {
        return beanService.hello();
    }
}