package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PostConstructorBean3 {

    @Autowired
    private CommonTestBean commonTestBean;

    public AtomicBoolean initialized = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        initialized.set(true);
    }

}
