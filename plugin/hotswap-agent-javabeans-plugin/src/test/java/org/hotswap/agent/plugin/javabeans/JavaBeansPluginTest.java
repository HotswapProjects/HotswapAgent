package org.hotswap.agent.plugin.javabeans;

import static org.junit.Assert.assertTrue;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;

/**
 * JavaBeans plugin test
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Vladimir Dvorak
 */
public class JavaBeansPluginTest {


    /**
     * Switch method implementation (using bean definition or interface).
     */
    @Test
    public void hotswapServiceTest() throws Exception {
        BeanInfo beanInfo1 = Introspector.getBeanInfo(TestBean1.class);
        assertTrue(containsMethod(beanInfo1, "helloWorld1"));
        swapClasses(TestBean1.class, TestBean2.class.getName());
        BeanInfo beanInfo2 = Introspector.getBeanInfo(TestBean1.class);
        assertTrue(!containsMethod(beanInfo2, "helloWorld1"));
        assertTrue(containsMethod(beanInfo2, "helloWorld2"));
    }

    private boolean containsMethod(BeanInfo beanInfo, String methodName) {
        MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
        for (MethodDescriptor md: methodDescriptors) {
            if (md.getName().equals(methodName))
                return true;
        }
        return false;
    }

    private void swapClasses(Class original, String swap) throws Exception {
        JavaBeansPlugin.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !JavaBeansPlugin.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in weld refresh?
        Thread.sleep(100);
    }
}
