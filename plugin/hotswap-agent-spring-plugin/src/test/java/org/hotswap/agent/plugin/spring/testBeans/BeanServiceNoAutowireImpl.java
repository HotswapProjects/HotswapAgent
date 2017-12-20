package org.hotswap.agent.plugin.spring.testBeans;

import org.springframework.beans.factory.annotation.Autowired;

public class BeanServiceNoAutowireImpl implements BeanService {
    BeanRepository beanRepository;
    //BeanChangedRepository beanRepository;

    public String hello() {
        return beanRepository.hello() + " Service";
    }

    public void setBeanRepository(BeanRepository beanRepository) {
        this.beanRepository = beanRepository;
    }
}
