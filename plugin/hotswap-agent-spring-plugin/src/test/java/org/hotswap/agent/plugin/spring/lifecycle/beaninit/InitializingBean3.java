package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InitializingBean3 implements InitializingBean {
    @Autowired
    private CommonTestBean commonTestBean;

    public AtomicBoolean initialized = new AtomicBoolean(false);
    @Override
    public void afterPropertiesSet() throws Exception {
        initialized.set(true);
    }
}
