package org.hotswap.agent.plugin.spring.transactional;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ComponentScan
@EnableTransactionManagement
public class TransactionalApplication {
}
