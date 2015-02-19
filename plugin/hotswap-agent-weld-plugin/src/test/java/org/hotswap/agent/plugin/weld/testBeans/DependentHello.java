package org.hotswap.agent.plugin.weld.testBeans;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 * Basic test bean with dependent scope.
 */
@Dependent
public class DependentHello {
    @Inject
    HelloServiceImpl helloService;

    public String hello() {
        return "Dependent:" + helloService.hello();
    }
}
