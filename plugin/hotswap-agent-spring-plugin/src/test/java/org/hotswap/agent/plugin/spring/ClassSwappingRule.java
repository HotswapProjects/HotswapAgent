package org.hotswap.agent.plugin.spring;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

public class ClassSwappingRule implements TestRule {

    private Set<Class<?>> swappedClasses;
    ConfigurableListableBeanFactory beanFactory;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                swappedClasses = new HashSet<>();
                try {
                    base.evaluate();
                } finally {
                    int reloadTimes = 1;
                    for (Class<?> cls : new ArrayList<>(swappedClasses))
                        swapClasses(cls, cls, reloadTimes++);
                }
            }
        };
    }

    public void swapClasses(Class<?> original, Class<?> swap, int reloadTimes) throws Exception {
        Assert.assertNotNull(beanFactory);
        if (original.equals(swap))
            swappedClasses.remove(original);
        else
            swappedClasses.add(original);
        HotSwapper.swapClasses(original, swap.getName());
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BeanFactoryAssistant.getBeanFactoryAssistant(beanFactory).getReloadTimes() >= reloadTimes;
            }
        }, 8000));

        // TODO do not know why sleep is needed, maybe a separate thread in Spring
        // refresh?
        Thread.sleep(100);
    }

    public void setBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
}
