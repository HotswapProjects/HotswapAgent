package org.hotswap.agent.plugin.mybatis.transformers;

import com.baomidou.mybatisplus.core.MybatisMapperRegistry;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.hotswap.agent.util.ReflectionHelper;

import static org.hotswap.agent.plugin.mybatis.transformers.MyBatisPlusTransformers.HA_SQLSESSIONFACTORY_BUILDER_FIELD;

public class MybatisPlusConfigurationCaller {

    public static boolean isMybatisObj(Configuration configuration, Class<?> clazz) {
        if (ConfigurationCaller.isMybatisObj(configuration, clazz)) {
            return true;
        }

        // is Mybatis Plus mapper obj
        Object mybatisMapperRegistryObj = ReflectionHelper.get(configuration, "mybatisMapperRegistry");
        if (mybatisMapperRegistryObj != null) {
            MybatisMapperRegistry mapperRegistry = (MybatisMapperRegistry) mybatisMapperRegistryObj;
            for (Class<?> mapper : mapperRegistry.getMappers()) {
                if (clazz.getName().equals(mapper.getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void setSqlSessionFactoryBuilder(DefaultSqlSessionFactory sqlSessionFactory, Object sqlSessionFactoryBuilder) {
        ReflectionHelper.set(sqlSessionFactory, HA_SQLSESSIONFACTORY_BUILDER_FIELD, sqlSessionFactoryBuilder);
    }

    public static SqlSessionFactoryBuilder getSqlSessionFactoryBuilder(DefaultSqlSessionFactory sqlSessionFactory) {
        Object o = ReflectionHelper.get(sqlSessionFactory, HA_SQLSESSIONFACTORY_BUILDER_FIELD);
        if (o == null) {
            return null;
        }
        return (SqlSessionFactoryBuilder) o;
    }
}
