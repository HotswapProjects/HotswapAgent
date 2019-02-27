package org.hotswap.agent.plugin.cdi;

import java.util.List;

public interface HaCdiExtraContext {
    public boolean containsBeanInstances(Class<?> beanClass);
    public List<Object> getBeanInstances(Class<?> beanClass);
}
