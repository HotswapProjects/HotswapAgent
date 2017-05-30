package org.hotswap.agent.plugin.spring.wildcardstest.beans.hotswap;


import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanChangedRepository;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BeanServiceImpl2 implements BeanService {
	String name = "Service2";
	
    @Autowired
    BeanChangedRepository beanRepository;
    
    public String hello() {
        return beanRepository.hello() + " " + name;
    }
	
    public String hello2() {
        return beanRepository.hello() + name;
    } 
    
}
