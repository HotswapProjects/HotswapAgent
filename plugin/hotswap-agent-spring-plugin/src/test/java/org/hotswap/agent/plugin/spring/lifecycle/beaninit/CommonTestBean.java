package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import org.springframework.stereotype.Component;

@Component
public class CommonTestBean {

    public String getName() {
        return "CommonTestBean";
    }
}
