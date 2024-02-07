package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InitializingBean2 implements InitializingBean {

    private static final AtomicBoolean globalInitialized = new AtomicBoolean(false);
    @Autowired
    private CommonTestBean commonTestBean;

    public AtomicBoolean initialized = new AtomicBoolean(false);
    @Override
    public void afterPropertiesSet() throws Exception {
        if (globalInitialized.get()) {
            throw new IllegalStateException("Bean already initialized");
        }
        initialized.set(true);
        globalInitialized.set(true);
    }
}
