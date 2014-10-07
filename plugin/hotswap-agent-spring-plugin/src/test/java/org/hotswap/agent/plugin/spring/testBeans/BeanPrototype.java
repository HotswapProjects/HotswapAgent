package org.hotswap.agent.plugin.spring.testBeans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Basic test bean with prototype scope.
 */
@Component
@Scope("prototype")
public class BeanPrototype {
    @Autowired
    BeanServiceImpl beanService;

    public String hello() {
        return beanService.hello() + " Prototype";
    }
}
