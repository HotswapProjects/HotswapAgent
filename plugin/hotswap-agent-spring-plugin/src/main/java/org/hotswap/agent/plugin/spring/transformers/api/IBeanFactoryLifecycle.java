package org.hotswap.agent.plugin.spring.transformers.api;

public interface IBeanFactoryLifecycle {

    void hotswapAgent$destroyBean(String beanName);

    boolean hotswapAgent$isDestroyedBean(String beanName);

    void hotswapAgent$clearDestroyBean();
}
