package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

@Component
public class PostConstructorBean4 {

    public AtomicBoolean initialized = new AtomicBoolean(false);
    @PostConstruct
    public void init() {
        initialized.set(true);
    }
}
