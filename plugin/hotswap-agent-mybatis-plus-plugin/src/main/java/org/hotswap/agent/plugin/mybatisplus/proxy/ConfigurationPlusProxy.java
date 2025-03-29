package org.hotswap.agent.plugin.mybatisplus.proxy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.proxy.SpringMybatisConfigurationProxy;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;
import org.hotswap.agent.plugin.mybatisplus.transformers.MybatisPlusConfigurationCaller;
import org.hotswap.agent.util.ReflectionHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The Class ConfigurationProxy.
 */
public class ConfigurationPlusProxy {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ConfigurationPlusProxy.class);

    private static Map<BaseBuilder, ConfigurationPlusProxy> proxiedConfigurations = new HashMap<>();

    public static ConfigurationPlusProxy getWrapper(BaseBuilder configBuilder) {
        /*
         * MyBatis runs in MyBatis-Spring mode, so there is no need to cache configuration-related data.
         * The related reload operations are handled by SpringMybatisConfigurationProxy
         */
        if (SpringMybatisConfigurationProxy.runningBySpringMybatis()) {
            LOGGER.debug("MyBatis runs in MyBatis-Spring mode, so there is no need to cache configuration-related data");
            return new ConfigurationPlusProxy(configBuilder);
        }
        LOGGER.info("configBuilder add");

        if (!proxiedConfigurations.containsKey(configBuilder)) {
            proxiedConfigurations.put(configBuilder, new ConfigurationPlusProxy(configBuilder));
        }
        return proxiedConfigurations.get(configBuilder);
    }

    public static void refreshProxiedConfigurations() {
        LOGGER.info("refreshProxiedConfigurations, size=" + proxiedConfigurations.size());
        for (ConfigurationPlusProxy wrapper : proxiedConfigurations.values())
            try {
                wrapper.refreshProxiedConfiguration();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private ConfigurationPlusProxy(BaseBuilder configBuilder) {
        this.configBuilder = configBuilder;
    }

    public void refreshProxiedConfiguration() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.configuration = new MybatisConfiguration();
        ReflectionHelper.invoke(configBuilder, MyBatisTransformers.REFRESH_METHOD);
    }

    private BaseBuilder configBuilder;
    private Configuration configuration;
    private Configuration proxyInstance;

    public Configuration proxy(Configuration origConfiguration) {
        if (origConfiguration == null) {
            return null;
        }
        this.configuration = origConfiguration;
        if (proxyInstance == null) {
            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(origConfiguration.getClass());

            MethodHandler handler = new MethodHandler() {
                @Override
                public Object invoke(Object self, Method overridden, Method forwarder,
                                     Object[] args) throws Throwable {
                    return overridden.invoke(configuration, args);
                }
            };

            try {
                proxyInstance = (Configuration) factory.create(new Class[0], null, handler);
            } catch (Exception e) {
                throw new Error("Unable instantiate Configuration proxy", e);
            }
        }
        return proxyInstance;
    }

    public static boolean isMybatisEntity(Class<?> clazz) {
        LOGGER.debug("isMybatisEntity, clazz={}, configuration size={}", clazz, proxiedConfigurations.size());
        for (ConfigurationPlusProxy configurationProxy : proxiedConfigurations.values()) {
            if (MybatisPlusConfigurationCaller.isMybatisObj(configurationProxy.configuration, clazz)) {
                return true;
            }
        }

        return false;
    }


}