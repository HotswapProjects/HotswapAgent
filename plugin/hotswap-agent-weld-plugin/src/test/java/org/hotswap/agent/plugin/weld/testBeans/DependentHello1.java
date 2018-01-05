package org.hotswap.agent.plugin.weld.testBeans;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 * Basic test bean with dependent scope.
 */
@Dependent
public class DependentHello1 {
    @Inject
    HelloServiceImpl1 helloService;

    public String hello() {
        return "DependentHello1.hello():" + helloService.hello();
    }
}
