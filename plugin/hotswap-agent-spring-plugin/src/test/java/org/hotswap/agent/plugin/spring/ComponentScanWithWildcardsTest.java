package org.hotswap.agent.plugin.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.hotswap.BeanServiceImpl2;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.hotswap.NewHelloServiceImpl;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanService;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.BeanServiceImpl;
import org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.NewHelloService;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Tests when the application context file is using component-scan with wildcards
 * 
 * See maven setup for javaagent and autohotswap settings.
 * 
 * @author Cedric Chabanois
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:wildcardsApplicationContext.xml" })
public class ComponentScanWithWildcardsTest {

    @Autowired
    AutowireCapableBeanFactory factory;

    @Test
    public void testHotswapService() throws Exception {
        BeanServiceImpl bean = factory.getBean(BeanServiceImpl.class);
        try {
            // Given
            assertEquals("Hello from Repository ServiceWithAspect", bean.hello());

            // When
            swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class.getName());

            // Then
            assertEquals("Hello from ChangedRepository Service2WithAspect", bean.hello());
            assertEquals("Hello from ChangedRepository Service2WithAspect", factory.getBean(BeanService.class).hello());
        } finally {
            swapClasses(BeanServiceImpl.class, BeanServiceImpl.class.getName());
            assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
        }
    }

    @Test
    public void testNewServiceClassFile() throws Exception {
        File file = null;
        try {
            // Given
            assertNoBean(NewHelloService.class);

            // When
            file = copyClassFileAndWaitForReload(NewHelloServiceImpl.class,
                    "org.hotswap.agent.plugin.spring.wildcardstest.beans.testbeans.NewHelloServiceImpl");

            // Then
            assertEquals("Hello from Repository NewHelloServiceWithAspect",
                    factory.getBean(NewHelloService.class).hello());
        } finally {
            if (file != null && file.exists()) {
                // explicit GC on windows to release class file
                System.gc();
                // we don't reload on delete
                if (!file.delete()) {
                    System.err.println("Warning - unable to delete class file " + file.getAbsolutePath() +
                            ". Run maven clean project or delete the file manually before running subsequent test runs.");
                }
            }
        }
    }

    private void assertNoBean(Class<?> clazz) {
        try {
            factory.getBean(clazz);
            fail();
        } catch (NoSuchBeanDefinitionException e) {

        }
    }

    private void swapClasses(Class<?> original, String swap) throws Exception {
        ClassPathBeanDefinitionScannerAgent.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        waitForReload(1000);
    }

    public static File copyClassFileAndWaitForReload(Class<?> clazz, String newName) throws Exception {
        ClassPathBeanDefinitionScannerAgent.reloadFlag = true;
        File file = copyClassFile(clazz, newName);
        try {
            waitForReload(10000);
        } catch (Throwable e) {
            file.delete();
            throw e;
        }
        return file;
    }

    private static void waitForReload(int timeout) throws InterruptedException {
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !ClassPathBeanDefinitionScannerAgent.reloadFlag;
            }
        }, timeout));
        // TODO do not know why sleep is needed, maybe a separate thread in Spring
        // refresh?
        Thread.sleep(100);
    }

    public static File copyClassFile(Class<?> clazz, String newName) throws Exception {
        String directoryName = clazz.getClassLoader().getResource("").getPath();
        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(clazz.getClassLoader()));
        CtClass ctClass = classPool.getAndRename(clazz.getName(), newName);
        ctClass.writeFile(directoryName);
        File file = new File(directoryName + File.separatorChar + newName.replace('.', File.separatorChar) + ".class");
        assertTrue(file.exists());
        return file;
    }

}
