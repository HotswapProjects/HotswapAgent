package org.hotswap.agent.plugin.mybatis.proxy;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;
import org.mybatis.spring.mapper.MapperFactoryBean;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class SpringMapperFactoryBean {

    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringMapperFactoryBean.class);

    protected static final List<MapperFactoryBean<Object>> mappingFactoryBeans = new LinkedList<>();

    public static void registerMapperFactoryBean(MapperFactoryBean<Object> factoryBean) {
        Class mapperInterface = (Class) ReflectionHelper.invoke(factoryBean, "getMapperInterface");

        LOGGER.debug("ready to add factory bean:" + mapperInterface.getName());
        for (MapperFactoryBean<Object> addedMappingFactoryBean : mappingFactoryBeans) {
            if (Objects.equals(factoryBean, addedMappingFactoryBean)) {
                return;
            }
        }
        mappingFactoryBeans.add(factoryBean);
        LOGGER.info("add factoryBean:" + mapperInterface.getName());
    }

    public static void reload() {
        for (MapperFactoryBean<Object> mappingFactoryBean : mappingFactoryBeans) {
            try {
                Class mapperInterface = (Class) ReflectionHelper.invoke(mappingFactoryBean, "getMapperInterface");
                ReflectionHelper.invoke(mappingFactoryBean, "checkDaoConfig");
                LOGGER.info("reload factoryBean: " + mapperInterface.getName());
            } catch (Exception e) {
                LOGGER.error("reload factoryBean error: " + e.getMessage());
            }
        }
    }
}
