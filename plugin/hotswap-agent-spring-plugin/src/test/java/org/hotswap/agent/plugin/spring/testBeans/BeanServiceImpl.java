package org.hotswap.agent.plugin.spring.testBeans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Basic service bean
 */
@Service
public class BeanServiceImpl implements BeanService {
    @Autowired
    BeanRepository beanRepository;
    //BeanChangedRepository beanRepository;

    public String hello() {
        return beanRepository.hello() + " Service";
    }
}
