package org.hotswap.agent.plugin.mybatisplus.transformers;

import com.baomidou.mybatisplus.core.MybatisMapperRegistry;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.plugin.mybatis.transformers.ConfigurationCaller;
import org.hotswap.agent.util.ReflectionHelper;

public class MybatisPlusConfigurationCaller {

    public static boolean isMybatisObj(Configuration configuration, Class<?> clazz) {
        if (ConfigurationCaller.isMybatisObj(configuration, clazz)) {
            return true;
        }

        // is Mybatis Plus mapper obj
        Object mybatisMapperRegistryObj = ReflectionHelper.get(configuration, "mybatisMapperRegistry");
        if (mybatisMapperRegistryObj == null) {
           return false;
        }

        MybatisMapperRegistry mapperRegistry = (MybatisMapperRegistry) mybatisMapperRegistryObj;
        for (Class<?> mapper : mapperRegistry.getMappers()) {
            if (clazz.getName().equals(mapper.getName())) {
                return true;
            }
        }

        return false;
    }
}
