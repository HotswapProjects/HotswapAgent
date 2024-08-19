package org.hotswap.agent.plugin.mybatisplus;

import org.hotswap.agent.logging.AgentLogger;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@SpringBootApplication
@MapperScan("org.hotswap.agent.plugin.mybatisplus.mapper")
public class Application {
    private static AgentLogger LOGGER = AgentLogger.getLogger(Application.class);

    private static ApplicationContext applicationContext;
    public static void main(String[] args) {
        LOGGER.info("Starting MyBatis Plus Application");
        applicationContext = SpringApplication.run(Application.class, args);
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        LOGGER.info("dataSourceInitializer");
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("org/hotswap/agent/plugin/mybatisplus/CreateDB.sql"));
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
