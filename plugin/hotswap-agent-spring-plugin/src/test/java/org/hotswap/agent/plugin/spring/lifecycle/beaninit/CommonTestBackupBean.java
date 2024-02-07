package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import org.springframework.stereotype.Component;

@Component
public class CommonTestBackupBean {

    public String getName() {
        return "CommonTestBackupBean";
    }
}
