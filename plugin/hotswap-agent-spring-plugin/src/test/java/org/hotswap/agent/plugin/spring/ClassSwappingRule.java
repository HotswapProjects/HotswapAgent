package org.hotswap.agent.plugin.spring;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ClassSwappingRule implements TestRule {

    private Set<Class<?>> swappedClasses;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                swappedClasses = new HashSet<>();
                try {
                    base.evaluate();
                } finally {
                    for (Class<?> cls : new ArrayList<>(swappedClasses))
                        swapClasses(cls, cls);
                }
            }
        };
    }

    public void swapClasses(Class<?> original, Class<?> swap) throws Exception {
        if (original.equals(swap))
            swappedClasses.remove(original);
        else
            swappedClasses.add(original);
        ClassPathBeanDefinitionScannerAgent.reloadFlag = true;
        HotSwapper.swapClasses(original, swap.getName());
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !ClassPathBeanDefinitionScannerAgent.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in Spring
        // refresh?
        Thread.sleep(100);
    }
}
