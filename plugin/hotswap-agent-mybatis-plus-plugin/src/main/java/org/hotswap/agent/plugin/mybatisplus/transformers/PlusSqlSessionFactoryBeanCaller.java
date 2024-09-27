package org.hotswap.agent.plugin.mybatisplus.transformers;

import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;
import org.hotswap.agent.util.ReflectionHelper;

public class PlusSqlSessionFactoryBeanCaller {

    public static Configuration proxyPlusConfiguration(Object factoryBean, Configuration configuration) {
        return (Configuration) ReflectionHelper.invoke(factoryBean, MybatisSqlSessionFactoryBean.class,
                MyBatisTransformers.CONFIGURATION_PROXY_METHOD, new Class[] {Configuration.class}, configuration);
    }

    public static void setPlusFactoryBean(SqlSessionFactoryBuilder builder, Object factoryBean) {
        ReflectionHelper.invoke(builder, MybatisSqlSessionFactoryBuilder.class,
                MyBatisTransformers.FACTORYBEAN_SET_METHOD, new Class[] {Object.class}, factoryBean);
    }
}
