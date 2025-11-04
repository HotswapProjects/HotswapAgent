package org.hotswap.agent.plugin.mybatis.proxy;

/**
 * Utility method to access reset methods for Proxy Configurations
 */
public class ProxyReset {
    public static void reset() {
        ConfigurationProxy.reset();
        SpringMybatisConfigurationProxy.reset();
    }
}
