package org.hotswap.agent.plugin.weld.testBeans;

import javax.enterprise.context.Dependent;

/**
 * Simple service.
 */
@Dependent
public interface HelloService {
    public String hello();
}
