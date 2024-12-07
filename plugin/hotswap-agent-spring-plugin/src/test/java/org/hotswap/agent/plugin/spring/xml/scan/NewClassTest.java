package org.hotswap.agent.plugin.spring.xml.scan;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.SpringReloadConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-scan/scanContext.xml"})
public class NewClassTest {
    private static AgentLogger LOGGER = AgentLogger.getLogger(NewClassTest.class);
    @Autowired
    private AbstractApplicationContext applicationContext;
    @Test
    public void swapSingleClassTest() throws Exception {
        BaseTestUtil.configMaxReloadTimes();
        LOGGER.info("NewClassTest.swapSingleClassTest." + applicationContext.getBeanFactory());
        assertNotNull(applicationContext.getBean(ScanItem.class).getName());
        assertNotNull(applicationContext.getBean(ScanItem2.class).getName());

        moveClass("org.hotswap.agent.plugin.spring.xml.scanbak.ScanBakItem1",
                "org.hotswap.agent.plugin.spring.xml.scan.ScanBakItem1", ScanItem.class.getClassLoader());
        moveClass("org.hotswap.agent.plugin.spring.xml.scanbak.ScanBakItem2",
                "org.hotswap.agent.plugin.spring.xml.scan.ScanBakItem2", ScanItem.class.getClassLoader());

        Thread.sleep(SpringReloadConfig.scaleTestSleepTime(12000));
        LOGGER.info("swap class finished");
        assertNotNull(applicationContext.getBean(ScanItem.class).getName());
        assertNotNull(applicationContext.getBean(ScanItem2.class).getName());
        assertNotNull(applicationContext.getBean("scanBakItem1"));
        assertNotNull(applicationContext.getBean("scanBakItem2"));
    }

    private void moveClass(String origClassName, String targetClassName, ClassLoader cl) throws Exception {
        File file = new ClassPathResource("xml-scan/scan-item.properties").getFile();
        String path = file.getAbsolutePath();
        String currentPath = path;
        while(true) {
            path = file.getAbsolutePath();
            if (path.endsWith("target") || path.endsWith("target/")) {
                break;
            }
            currentPath = path;
            file = file.getParentFile();
        }

        Class c = HotSwapper.newClass(targetClassName, currentPath, cl);
        HotSwapper.swapClasses(c, origClassName);
    }
}
