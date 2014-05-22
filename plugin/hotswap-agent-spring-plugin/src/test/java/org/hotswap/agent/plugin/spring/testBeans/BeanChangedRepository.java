package org.hotswap.agent.plugin.spring.testBeans;

import org.springframework.stereotype.Repository;

/**
 * Change to this repository to check reinitialization after hotswap.
 */
@Repository
public class BeanChangedRepository {
    public String hello() {
        return "Hello from ChangedRepository";
    }
}
