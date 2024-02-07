package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


public class InitMethodBean3 {

    @Autowired
    private CommonTestBean commonTestBean;

    public AtomicBoolean initialized = new AtomicBoolean(false);

    public void init() throws Exception {
        initialized.set(true);
    }
}
