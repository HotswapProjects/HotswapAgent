package org.hotswap.agent.plugin.mybatis.transformers;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hotswap.agent.util.ReflectionHelper;
import org.mybatis.spring.SqlSessionFactoryBean;

public class SqlSessionFactoryBeanCaller {

    public static Configuration proxyConfiguration(Object factoryBean, Configuration configuration) {
        return (Configuration) ReflectionHelper.invoke(factoryBean, SqlSessionFactoryBean.class,
                MyBatisTransformers.CONFIGURATION_PROXY_METHOD, new Class[] {Configuration.class}, configuration);
    }

    public static void setFactoryBean(SqlSessionFactoryBuilder builder, Object factoryBean) {
        ReflectionHelper.invoke(builder, SqlSessionFactoryBuilder.class,
                MyBatisTransformers.FACTORYBEAN_SET_METHOD, new Class[] {Object.class}, factoryBean);
    }
}
