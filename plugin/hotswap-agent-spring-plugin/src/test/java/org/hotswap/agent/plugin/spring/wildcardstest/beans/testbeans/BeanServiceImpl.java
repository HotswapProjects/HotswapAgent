package org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BeanServiceImpl implements BeanService {

    @Autowired
    BeanRepository beanRepository;

    public String hello() {
        return beanRepository.hello() + " Service";
    }	
	
}
