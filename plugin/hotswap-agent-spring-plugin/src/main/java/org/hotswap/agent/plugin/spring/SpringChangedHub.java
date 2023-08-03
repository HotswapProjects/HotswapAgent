package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.util.ResourceUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SpringChangedHub {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringChangedHub.class);
    public static final int INIT = 1;
    public static final int CHANGING = 2;
    public static final int RELOADED = 3;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "SpringReload");
        }
    });


    private AtomicInteger status = new AtomicInteger(1);
    private AtomicInteger waitTimes = new AtomicInteger(0);

    private DefaultListableBeanFactory defaultListableBeanFactory;
    private static ClassLoader classLoader;
    private static Map<DefaultListableBeanFactory, SpringChangedHub> springChangedHubs = new ConcurrentHashMap<>(2);
    final SpringGlobalCaches springGlobalCaches;
    private SpringReload current;
    private SpringReload next;
    private SpringReload failed;


    public SpringChangedHub(DefaultListableBeanFactory defaultListableBeanFactory) {
        springGlobalCaches = new SpringGlobalCaches();
        current = new SpringReload(defaultListableBeanFactory, springGlobalCaches);
        next = new SpringReload(defaultListableBeanFactory, springGlobalCaches);
        failed = new SpringReload(defaultListableBeanFactory, springGlobalCaches);
        this.defaultListableBeanFactory = defaultListableBeanFactory;
    }

    void init() {
        scheduler.scheduleWithFixedDelay(this::reload, 1, 2, TimeUnit.SECONDS);
    }

    public static SpringChangedHub getInstance(DefaultListableBeanFactory beanFactory) {
        if (springChangedHubs.get(beanFactory) == null) {
            synchronized (SpringChangedHub.class) {
                if (springChangedHubs.get(beanFactory) == null) {
                    SpringChangedHub springChangedHub = new SpringChangedHub(beanFactory);
                    springChangedHubs.put(beanFactory, springChangedHub);
                    springChangedHub.init();
                }
            }
        }
        return springChangedHubs.get(beanFactory);
    }

    public static void setClassLoader(ClassLoader classLoader) {
        SpringChangedHub.classLoader = classLoader;
    }

    public static void addChangedClass(Class clazz) {
        for (SpringChangedHub springChangedHub : springChangedHubs.values()) {
            springChangedHub.addClass(clazz);
        }
    }

    public static void addChangedXml(URL xmlUrl) {
        for (SpringChangedHub springChangedHub : springChangedHubs.values()) {
            springChangedHub.addXml(xmlUrl);
        }
    }

    public static void addChangedProperty(URL property) {
        for (SpringChangedHub springChangedHub : springChangedHubs.values()) {
            springChangedHub.addProperty(property);
        }
    }

    public void addClass(Class clazz) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addClass(clazz);
        } else if (status.intValue() == RELOADED) {
            next.addClass(clazz);
        }
    }

    public void addProperty(URL property) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addProperty(property);
        } else if (status.intValue() == RELOADED) {
            next.addProperty(property);
        }
    }

    public void addXml(URL xml) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addXml(xml);
        } else if (status.intValue() == RELOADED) {
            next.addXml(xml);
        }
    }

    private void reload() {
        // delay twice
        if (waitTimes.get() > 1) {
            waitTimes.incrementAndGet();
            if (status.compareAndSet(CHANGING, RELOADED)) {
                // relead
                try {
                    current.reload();
                } catch (Exception e) {
                    LOGGER.error("reload spring failed", e);
                }
                finishReload();
                waitTimes.set(0);
            }
        } else if (status.get() == CHANGING) {
            waitTimes.incrementAndGet();
        }
    }

    public static void collectPlaceholderProperties(ConfigurableListableBeanFactory configurableListableBeanFactory) {
        if (!(configurableListableBeanFactory instanceof DefaultListableBeanFactory)) {
            return;
        }
        getInstance((DefaultListableBeanFactory) configurableListableBeanFactory).doCollectPlaceHolderProperties();
    }

    private void doCollectPlaceHolderProperties() {
        String[] beanNames = defaultListableBeanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = defaultListableBeanFactory.getBeanDefinition(beanName);
            doCollectPlaceHolderProperties0(beanName, beanDefinition);
        }
    }

    private void doCollectPlaceHolderProperties0(String beanName, BeanDefinition beanDefinition) {

        for (PropertyValue pv : beanDefinition.getPropertyValues()) {
            if (containPlaceHolder(pv.getValue(), beanName, beanDefinition)) {
                return;
            }
        }
        if (!beanDefinition.hasConstructorArgumentValues()) {
            return;
        }
        for (ConstructorArgumentValues.ValueHolder valueHolder : beanDefinition.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
            if (containPlaceHolder(valueHolder.getValue(), beanName, beanDefinition)) {
                return;
            }
        }
        for (ConstructorArgumentValues.ValueHolder valueHolder : beanDefinition.getConstructorArgumentValues().getGenericArgumentValues()) {
            if (containPlaceHolder(valueHolder.getValue(), beanName, beanDefinition)) {
                return;
            }
        }
    }

    private boolean containPlaceHolder(Object object, String beanName, BeanDefinition beanDefinition) {
        if (!isPlaceHolderBean(object)) {
            return false;
        }
        if (beanDefinition instanceof AbstractBeanDefinition) {
            String path = ResourceUtils.getPath(((AbstractBeanDefinition) beanDefinition).getResource());
            if (path == null) {
                return false;
            }
            springGlobalCaches.placeHolderXmlRelation.put(beanName, path);
            return true;
        }
        return false;
    }

    private boolean isPlaceHolderBean(Object v) {
        String value = null;
        if (v instanceof TypedStringValue) {
            value = ((TypedStringValue) v).getValue();
        } else if (v instanceof String) {
            value = (String) v;
        }
        if (value == null) {
            return false;
        }
        if (value.startsWith(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX) &&
                value.endsWith(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX)) {
            return true;
        }
        return false;
    }

    public void finishReload() {
        current = next;
        if (status.compareAndSet(RELOADED, INIT)) {
            current.appendAll(failed);
            failed = new SpringReload(defaultListableBeanFactory, springGlobalCaches);
            next = new SpringReload(defaultListableBeanFactory, springGlobalCaches);
        }
    }

}
