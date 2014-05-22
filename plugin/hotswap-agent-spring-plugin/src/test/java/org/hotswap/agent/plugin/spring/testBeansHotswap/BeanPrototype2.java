package org.hotswap.agent.plugin.spring.testBeansHotswap;

import org.hotswap.agent.plugin.spring.testBeans.BeanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Basic test bean with prototype scope.
 */
@Component
@Scope("prototype")
public class BeanPrototype2 {
    @Autowired
    BeanRepository beanRepository;

    public String hello() {
        return beanRepository.hello() + " Prototype2";
    }
}
