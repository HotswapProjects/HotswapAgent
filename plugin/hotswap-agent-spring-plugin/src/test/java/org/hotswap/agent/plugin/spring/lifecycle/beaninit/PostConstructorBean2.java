package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PostConstructorBean2 {
    private static final AtomicBoolean globalInitialized = new AtomicBoolean(false);

    @Autowired
    private CommonTestBean commonTestBean;

    public AtomicBoolean initialized = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        if (globalInitialized.get()) {
            throw new IllegalStateException("Bean already initialized");
        }
        initialized.set(true);
        globalInitialized.set(true);
    }

}
