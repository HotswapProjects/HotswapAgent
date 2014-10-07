package org.hotswap.agent.plugin.spring.testBeansHotswap;

import org.hotswap.agent.plugin.spring.testBeans.BeanChangedRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Change repository, use Autowiring to check that new bean is really created. Test Service that uses this
 * repository that it is indeed recreated with new configuration.
 */
@Repository
public class BeanRepository2 {
    @Autowired
    BeanChangedRepository beanChangedRepository;

    public String hello() {
        return beanChangedRepository.hello() + "2";
    }

    public String helloNewMethod() {
        return "Repository new method";
    }
}
