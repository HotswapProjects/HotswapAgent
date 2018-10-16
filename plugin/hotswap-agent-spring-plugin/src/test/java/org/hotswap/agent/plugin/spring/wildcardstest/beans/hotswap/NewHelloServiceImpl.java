package org.hotswap.agent.plugin.spring.wildcardstest.beans.hotswap;

import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanRepository;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.NewHelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewHelloServiceImpl implements NewHelloService {

    @Autowired
    BeanRepository beanRepository;

    public String hello() {
        return beanRepository.hello() + " NewHelloService";
    }	
	
}
