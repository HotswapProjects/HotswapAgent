package org.hotswap.agent.plugin.owb_jakarta.testBeansHotswap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.hotswap.agent.plugin.owb_jakarta.testBeans.TestItercepting;

@ApplicationScoped
public class InterceptedBean2 {

    @Inject
    InterceptedBean2 thisBean;

    public String hello() {
        return "InterceptedBean2.hello():" + thisBean.hello2();
    }

    @TestItercepting
    public String hello2() {
        return "InterceptedBean2.hello2()";
    }

    @TestItercepting
    public String hello3() {
        return "InterceptedBean2.hello3()";
    }
}