package org.hotswap.agent.plugin.owb_jakarta.testBeans;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InterceptedBean {
    @TestItercepting
    public String hello() {
        return "InterceptedBean.hello()";
    }
}
