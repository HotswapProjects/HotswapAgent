package org.hotswap.agent.plugin.weld.testBeansHotswap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.hotswap.agent.plugin.weld.testBeans.TestItercepting;

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