package org.hotswap.agent.plugin.spring.testBeansHotswap;

import org.hotswap.agent.plugin.spring.testBeans.BeanChangedRepository;
import org.hotswap.agent.plugin.spring.testBeans.BeanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Basic service bean
 */
@Service
public class BeanServiceImpl2 implements BeanService {
    String name = "Service2";

    @Autowired
    BeanChangedRepository beanChangedRepository;

    public String hello() {
        return beanChangedRepository.hello() + " "  + name;
    }

    public String helloNewMethod() {
        return "Hello from helloNewMethod "  + name;
    }
}
