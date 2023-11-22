package org.hotswap.agent.plugin.spring.boot.listener;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.files.PropertiesChangeEvent;
import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.hotswap.agent.plugin.spring.listener.SpringEventSource;
import org.hotswap.agent.plugin.spring.listener.SpringListener;
import org.hotswap.agent.plugin.spring.reload.BeanChangeEvent;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.spring.util.ClassUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

public class PropertySourceChangeBootListener implements SpringListener<SpringEvent<?>> {

    public static void register() {
        SpringEventSource.INSTANCE.addListener(new PropertySourceChangeBootListener());
    }

    private static AgentLogger LOGGER = AgentLogger.getLogger(PropertySourceChangeBootListener.class);

    @Override
    public DefaultListableBeanFactory beanFactory() {
        return null;
    }

    @Override
    public void onEvent(SpringEvent<?> event) {
        if (event instanceof PropertiesChangeEvent) {
            refreshConfigurationProperties(event.getBeanFactory());
        }
    }

    private void refreshConfigurationProperties(ConfigurableListableBeanFactory beanFactory) {
        for (String singleton : beanFactory.getSingletonNames()) {
            Object bean = beanFactory.getSingleton(singleton);
            Class beanClass = ClassUtils.getUserClass(bean.getClass());
            // fixme temperately skip spring boot classes
//            if (beanClass.getName().startsWith("org.springframework.boot")) {
//                LOGGER.trace("skip refresh configuration properties: {}", beanClass);
//                continue;
//            }

            if (AnnotationHelper.hasAnnotation(beanClass, ConfigurationProperties.class.getName())) {
                LOGGER.debug("refresh configuration properties: {}", beanClass);
                String[] beanNames = beanFactory.getBeanNamesForType(beanClass);
                if (beanNames != null && beanNames.length > 0) {
                    SpringEventSource.INSTANCE.fireEvent(new BeanChangeEvent(beanNames, beanFactory));
                }
            }
        }
    }
}
