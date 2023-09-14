package org.hotswap.agent.plugin.spring;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.scanner.BeanDefinitionChangeEvent;
import org.hotswap.agent.plugin.spring.listener.SpringEvent;
import org.hotswap.agent.plugin.spring.listener.SpringEventSource;
import org.hotswap.agent.plugin.spring.listener.SpringListener;
import org.hotswap.agent.plugin.spring.scanner.ClassChangeEvent;
import org.hotswap.agent.plugin.spring.utils.ResourceUtils;
import org.hotswap.agent.util.spring.util.ObjectUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SpringChangedHub implements SpringListener<SpringEvent> {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringChangedHub.class);
    private static final int DEFAULT_DELAY_PERIOD = 1500;
    public static final int INIT = 1;
    public static final int CHANGING = 2;
    public static final int RELOADED = 3;

    static int maxWaitTimes = 5;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "SpringReload");
        }
    });


    private AtomicInteger status = new AtomicInteger(1);
    private AtomicInteger waitTimes = new AtomicInteger(0);

    private DefaultListableBeanFactory defaultListableBeanFactory;
    private static ClassLoader appClassLoader;
    private static Map<DefaultListableBeanFactory, SpringChangedHub> springChangedHubs = new ConcurrentHashMap<>(2);
    final BeanFactoryAssistant beanFactoryAssistant;
    final AtomicBoolean pause = new AtomicBoolean(false);
    private SpringReload current;
    private SpringReload next;
    private SpringReload failed;
    private BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();


    public SpringChangedHub(DefaultListableBeanFactory defaultListableBeanFactory) {
        beanFactoryAssistant = new BeanFactoryAssistant(defaultListableBeanFactory);
        current = new SpringReload(defaultListableBeanFactory, beanFactoryAssistant);
        current.setBeanNameGenerator(beanNameGenerator);
        next = new SpringReload(defaultListableBeanFactory, beanFactoryAssistant);
        next.setBeanNameGenerator(beanNameGenerator);
        failed = new SpringReload(defaultListableBeanFactory, beanFactoryAssistant);
        this.defaultListableBeanFactory = defaultListableBeanFactory;
        SpringEventSource.INSTANCE.addListener(this);
    }

    void init() {
        scheduler.scheduleWithFixedDelay(this::reload, 1, DEFAULT_DELAY_PERIOD, TimeUnit.MILLISECONDS);
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
        SpringChangedHub.appClassLoader = classLoader;
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

    void addClass(Class clazz) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addClass(clazz);
        } else if (status.intValue() == RELOADED) {
            next.addClass(clazz);
        }
    }

    void addProperty(URL property) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addProperty(property);
        } else if (status.intValue() == RELOADED) {
            next.addProperty(property);
        }
    }

    void addXml(URL xml) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addXml(xml);
        } else if (status.intValue() == RELOADED) {
            next.addXml(xml);
        }
    }

    void addNewBean(BeanDefinitionRegistry registry, BeanDefinitionHolder beanDefinitionHolder) {
        if (status.compareAndSet(INIT, CHANGING) || status.intValue() == CHANGING) {
            current.addScanNewBean(registry, beanDefinitionHolder);
        } else if (status.intValue() == RELOADED) {
            next.addScanNewBean(registry, beanDefinitionHolder);
        }
    }

    private void reload() {
        // delay three times
        if (waitTimes.get() >= maxWaitTimes) {
            waitTimes.incrementAndGet();
            if (pause.get()) {
                LOGGER.trace("spring reload pause: {}", ObjectUtils.identityToString(current.beanFactory));
                return;
            }
            if (status.compareAndSet(CHANGING, RELOADED)) {
                // relead
                try {
                    current.reload();
                } catch (Exception e) {
                    LOGGER.error("reload spring failed: {}", e, ObjectUtils.identityToString(current.beanFactory));
                }
                finishReload();
                waitTimes.set(0);
            }
        } else if (status.get() == CHANGING) {
            if (waitTimes.get() == 0) {
                int restTime = maxWaitTimes * DEFAULT_DELAY_PERIOD / 1000;
                LOGGER.info("waiting to start reload '{}', it will start after {}s", ObjectUtils.identityToString(beanFactory()), restTime);
            }
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

        if (beanDefinition.getPropertyValues() != null) {
            for (PropertyValue pv : beanDefinition.getPropertyValues().getPropertyValues()) {
                if (containPlaceHolder(pv.getValue(), beanName, beanDefinition)) {
                    return;
                }
            }
        }
        if (beanDefinition.getConstructorArgumentValues().isEmpty()) {
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
            beanFactoryAssistant.placeHolderXmlRelation.put(beanName, path);
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
            failed = new SpringReload(defaultListableBeanFactory, beanFactoryAssistant);
            next = new SpringReload(defaultListableBeanFactory, beanFactoryAssistant);
            next.setBeanNameGenerator(this.beanNameGenerator);
        }
    }

    @Override
    public DefaultListableBeanFactory beanFactory() {
        return defaultListableBeanFactory;
    }

    @Override
    public void onEvent(SpringEvent event) {
        if (event instanceof BeanDefinitionChangeEvent) {
            BeanDefinitionChangeEvent beanDefinitionChangeEvent = (BeanDefinitionChangeEvent) event;
            addNewBean(beanDefinitionChangeEvent.getBeanFactory(), beanDefinitionChangeEvent.getSource());
        } else if (event instanceof ClassChangeEvent) {
            ClassChangeEvent changeEvent = (ClassChangeEvent) event;
            addClass(changeEvent.getSource());
        }
    }

    public void setPause(boolean pause) {
        this.pause.set(pause);
    }
}
