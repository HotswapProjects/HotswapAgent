package org.hotswap.agent.plugin.spring.testBeans;

import org.springframework.stereotype.Repository;

/**
 * Basic repository bean
 */
@Repository
public class BeanRepository {
    public String hello() {
        return "Hello from Repository";
    }
}
