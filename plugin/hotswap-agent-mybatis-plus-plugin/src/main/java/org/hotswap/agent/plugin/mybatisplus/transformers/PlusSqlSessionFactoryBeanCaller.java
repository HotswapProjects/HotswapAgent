package org.hotswap.agent.plugin.mybatisplus.transformers;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;
import org.hotswap.agent.util.ReflectionHelper;

public class PlusSqlSessionFactoryBeanCaller {

    public static Configuration proxyPlusConfiguration(Object factoryBean, Configuration configuration) {
        return (Configuration) ReflectionHelper.invoke(factoryBean, MybatisSqlSessionFactoryBean.class,
                MyBatisTransformers.CONFIGURATION_PROXY_METHOD, new Class[] {Configuration.class}, configuration);
    }

    public static void setPlusFactoryBean(Object builder, Object factoryBean) {
        ReflectionHelper.invoke(builder, builder.getClass(),
                MyBatisTransformers.FACTORYBEAN_SET_METHOD, new Class[] {Object.class}, factoryBean);
    }
}
