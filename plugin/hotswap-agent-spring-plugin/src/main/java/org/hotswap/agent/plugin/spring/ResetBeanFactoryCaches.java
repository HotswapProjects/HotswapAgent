package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Field;
import java.util.List;

public class ResetBeanFactoryCaches {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResetBeanFactoryCaches.class);

    public static void reset(DefaultListableBeanFactory beanFactory) {
        resetEmbeddedValueResolvers(beanFactory);
    }

    private static void resetEmbeddedValueResolvers(DefaultListableBeanFactory beanFactory) {
        try {
            Field field = AbstractBeanFactory.class.getDeclaredField("embeddedValueResolvers");
            field.setAccessible(true);
            List<StringValueResolver> embeddedValueResolvers = (List<StringValueResolver>) field.get(beanFactory);
            if (embeddedValueResolvers != null && !embeddedValueResolvers.isEmpty()) {
                embeddedValueResolvers.clear();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }
}
