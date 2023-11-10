package org.hotswap.agent.plugin.spring.testBeans.iabpp;

import org.hotswap.agent.plugin.spring.testBeans.BeanServiceImplNoAspect;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class IABPP implements InstantiationAwareBeanPostProcessor {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if (beanClass == BeanCreatedByIABPP.class) {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(beanClass);
            enhancer.setCallback(new MI());
            BeanCreatedByIABPP beanCreatedByIABPP = (BeanCreatedByIABPP) enhancer.create();
            beanCreatedByIABPP.beanService = applicationContext.getBean(BeanServiceImplNoAspect.class);
            return beanCreatedByIABPP;
        }
        return null;
    }

    static class MI implements MethodInterceptor {

        @Override
        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy)
                throws Throwable {
            Object invokeSuper = methodProxy.invokeSuper(o, objects);
            return invokeSuper;
        }
    }

}

