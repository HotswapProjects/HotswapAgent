package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;

public class InitMethodBean2 {
    private static final AtomicBoolean globalInitialized = new AtomicBoolean(false);

    @Autowired
    private CommonTestBean commonTestBean;

    public AtomicBoolean initialized = new AtomicBoolean(false);

    public void init() throws Exception {
        if (globalInitialized.get()) {
            throw new IllegalStateException("Bean already initialized");
        }
        initialized.set(true);
        globalInitialized.set(true);
    }
}
