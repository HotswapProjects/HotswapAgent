package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InterceptedBean {
    @TestItercepting
    public String hello() {
        return "InterceptedBean.hello()";
    }
}
