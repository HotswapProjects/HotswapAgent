package org.hotswap.agent.plugin.spring.xml.scan;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.xml.placeholder.Item2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-scan/scanContext.xml"})
public class NewClassTest {
    @Autowired
    private AbstractApplicationContext applicationContext;
    @Test
    public void swapSingleClassTest() throws Exception {
        System.out.println("NewClassTest.swapSingleClassTest." + applicationContext.getBeanFactory());
        System.out.println("NewClassTest.swapSingleClassTest." + applicationContext.getBeanFactory());
        assertNotNull(applicationContext.getBean(ScanItem.class).getName());
        assertNotNull(applicationContext.getBean(ScanItem2.class).getName());

        moveClass("org.hotswap.agent.plugin.spring.xml.scanbak.ScanBakItem1",
                "org.hotswap.agent.plugin.spring.xml.scan.ScanBakItem1", ScanItem.class.getClassLoader());
        moveClass("org.hotswap.agent.plugin.spring.xml.scanbak.ScanBakItem2",
                "org.hotswap.agent.plugin.spring.xml.scan.ScanBakItem2", ScanItem.class.getClassLoader());

        Thread.sleep(10000);

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
