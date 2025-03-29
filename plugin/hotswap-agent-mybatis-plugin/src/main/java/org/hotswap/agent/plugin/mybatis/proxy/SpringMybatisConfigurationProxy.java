package org.hotswap.agent.plugin.mybatis.proxy;

import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.plugin.mybatis.transformers.ConfigurationCaller;
import org.hotswap.agent.util.ReflectionHelper;

import java.util.HashMap;
import java.util.Map;

public class SpringMybatisConfigurationProxy {

    protected static Map<Object, SpringMybatisConfigurationProxy> proxiedConfigurations = new HashMap<>();

    public SpringMybatisConfigurationProxy(Object sqlSessionFactoryBean) {
        this.sqlSessionFactoryBean = sqlSessionFactoryBean;
    }

    public static SpringMybatisConfigurationProxy getWrapper(Object sqlSessionFactoryBean) {
        if (!proxiedConfigurations.containsKey(sqlSessionFactoryBean)) {
            proxiedConfigurations.put(sqlSessionFactoryBean, new SpringMybatisConfigurationProxy(sqlSessionFactoryBean));
        }
        return proxiedConfigurations.get(sqlSessionFactoryBean);
    }

    public static boolean runningBySpringMybatis() {
        return !proxiedConfigurations.isEmpty();
    }

    public static void refreshProxiedConfigurations() {
        for (SpringMybatisConfigurationProxy wrapper : proxiedConfigurations.values())
            try {
                ConfigurationCaller.setInReload(wrapper.configuration, true);
                wrapper.refreshProxiedConfiguration();
                SpringMapperFactoryBean.reload();
                ConfigurationCaller.setInReload(wrapper.configuration, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    public void refreshProxiedConfiguration() {
        Object newSqlSessionFactory = ReflectionHelper.invoke(this.sqlSessionFactoryBean, "buildSqlSessionFactory");
        this.configuration = (Configuration) ReflectionHelper.get(newSqlSessionFactory, "configuration");
    }

    private Object sqlSessionFactoryBean;
    public Configuration configuration;

    public Configuration proxy(Configuration origConfiguration) {
        configuration = origConfiguration;
        return configuration;
    }

    public static boolean isMybatisEntity(Class<?> clazz) {
        for (SpringMybatisConfigurationProxy configurationProxy : proxiedConfigurations.values()) {
            if (ConfigurationCaller.isMybatisObj(configurationProxy.configuration, clazz)) {
                return true;
            }
        }

        return false;
    }
}
