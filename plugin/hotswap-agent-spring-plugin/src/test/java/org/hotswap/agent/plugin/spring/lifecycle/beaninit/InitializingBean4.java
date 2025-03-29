package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class InitializingBean4 implements InitializingBean {
    public AtomicBoolean initialized = new AtomicBoolean(false);
    @Override
    public void afterPropertiesSet() throws Exception {
        initialized.set(true);
    }
}
