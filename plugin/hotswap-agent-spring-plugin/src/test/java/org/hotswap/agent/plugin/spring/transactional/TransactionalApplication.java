package org.hotswap.agent.plugin.spring.transactional;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author: 周子恒
 * @create: 2023-05-31 13:49
 **/
@ComponentScan
@EnableTransactionManagement
public class TransactionalApplication {
}
