package org.hotswap.agent.plugin.spring.lifecycle.beaninit;

import java.util.concurrent.atomic.AtomicBoolean;

public class InitMethodBean4 {

    public AtomicBoolean initialized = new AtomicBoolean(false);

    public void init() throws Exception {
        initialized.set(true);
    }
}
