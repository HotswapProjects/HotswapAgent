package org.hotswap.agent.plugin.mybatisplus.proxy;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.proxy.SpringMybatisConfigurationProxy;
import org.hotswap.agent.plugin.mybatisplus.transformers.MybatisPlusConfigurationCaller;

public class SpringMybatisPlusConfigurationProxy extends SpringMybatisConfigurationProxy {

    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringMybatisPlusConfigurationProxy.class);

    public SpringMybatisPlusConfigurationProxy(Object sqlSessionFactoryBean) {
        super(sqlSessionFactoryBean);
    }

    public static boolean isMybatisPlusEntity(Class<?> clazz) {
        LOGGER.debug("isMybatisEntity, clazz={}, configuration size={}", clazz, proxiedConfigurations.size());
        for (SpringMybatisConfigurationProxy configurationProxy : proxiedConfigurations.values()) {
            if (MybatisPlusConfigurationCaller.isMybatisObj(configurationProxy.configuration, clazz)) {
                return true;
            }
        }

        return false;
    }
}
