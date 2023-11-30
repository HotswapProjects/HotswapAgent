/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.boot.properties;

import org.hotswap.agent.plugin.spring.boot.BaseTestUtil;
import org.hotswap.agent.plugin.spring.boot.properties.app.*;
import org.hotswap.agent.plugin.spring.listener.SpringEventSource;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("ps")
@TestPropertySource("classpath:ps/test.properties")
public class PropertySourceTest {

    @Autowired
    private AbstractApplicationContext applicationContext;

    private static PropertiesChangeMockListener propertiesChangeMockListener = new PropertiesChangeMockListener();

    @BeforeClass
    public static void beforeClass() {
        BaseTestUtil.configMaxReloadTimes();
        SpringEventSource.INSTANCE.addListener(propertiesChangeMockListener);
    }

    @Test
    public void testBinderUtils() throws Exception {
        Test10Properties test10Properties = applicationContext.getBean(Test10Properties.class);
        Test80Properties test80Properties = applicationContext.getBean(Test80Properties.class);
        Test50Service test50Service = applicationContext.getBean(Test50Service.class);
        Test80Service test80Service = applicationContext.getBean(Test80Service.class);
        printObject(test10Properties, test50Service, test80Properties, test80Service);
        assert10And50(test10Properties, test50Service, 0, "");
        assert80(test80Properties, test80Service, "ps-properties-l1", "ps-properties-l2",
                "ps-yaml-l3", "base-properties-l4", "base-yaml-l5");
        Assert.assertTrue(propertiesChangeMockListener.newValueMap().isEmpty());
        Assert.assertTrue(propertiesChangeMockListener.oldValueMap().isEmpty());
        // swap test.properties
        modifyFile("ps/hotswap/test-bak.properties", "ps/test.properties");
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 1);
            }
        }, 12000));
        printObject(test10Properties, test50Service, test80Properties, test80Service);
        test10Properties = applicationContext.getBean(Test10Properties.class);
        test80Properties = applicationContext.getBean(Test80Properties.class);
        test50Service = applicationContext.getBean(Test50Service.class);
        test80Service = applicationContext.getBean(Test80Service.class);
        assert10And50(test10Properties, test50Service, "test-properties-world-v2", "ps-properties-greeting",
                "test-properties-geek-v2", "base-properties-boot", 8002, "app-properties-l10", "test-properties-l11-v2",
                "test-properties-l12-v2");
        assert80(test80Properties, test80Service, "ps-properties-l1", "ps-properties-l2",
                "ps-yaml-l3", "base-properties-l4", "base-yaml-l5");
        assertChangeCount(10);
        assertContainKey("properties.l10.l1", "properties.l10.l3", "properties.l10.l5", "properties.l10.l11", "properties.l10.l12");
        assertContent("properties.l10.l1", "test-properties-world", "test-properties-world-v2",
                "properties.l10.l3", "test-properties-geek", "test-properties-geek-v2",
                "properties.l10.l5", "8000", "8002",
                "properties.l10.l11", "test-properties-l11", "test-properties-l11-v2",
                "properties.l10.l12", "test-properties-l12", "test-properties-l12-v2");
        propertiesChangeMockListener.clear();

        // swap app.properties
        modifyFile("ps/hotswap/app-bak.properties", "ps/app.properties");
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 2);
            }
        }, 120000));
        test10Properties = applicationContext.getBean(Test10Properties.class);
        test80Properties = applicationContext.getBean(Test80Properties.class);
        test50Service = applicationContext.getBean(Test50Service.class);
        test80Service = applicationContext.getBean(Test80Service.class);
        assert10And50(test10Properties, test50Service, "test-properties-world-v2", "ps-properties-greeting",
                "test-properties-geek-v2", "base-properties-boot", 8002, "app-properties-l10-v2", "test-properties-l11-v2",
                "test-properties-l12-v2");
        assert80(test80Properties, test80Service, "ps-properties-l1", "ps-properties-l2",
                "ps-yaml-l3", "base-properties-l4", "base-yaml-l5");
        assertChangeCount(2);
        propertiesChangeMockListener.clear();

        // swap application.yaml and application.properties
        modifyFile("ps/hotswap/application-bak.properties", "application.properties");
        modifyFile("ps/hotswap/application-bak.yaml", "application.yaml");
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 3);
            }
        }, 120000));
        test10Properties = applicationContext.getBean(Test10Properties.class);
        test80Properties = applicationContext.getBean(Test80Properties.class);
        test50Service = applicationContext.getBean(Test50Service.class);
        test80Service = applicationContext.getBean(Test80Service.class);
        assert10And50(test10Properties, test50Service, "test-properties-world-v2", "ps-properties-greeting",
                "test-properties-geek-v2", "base-properties-boot-v2", 8002, "app-properties-l10-v2", "test-properties-l11-v2",
                "test-properties-l12-v2");
        assert80(test80Properties, test80Service, "ps-properties-l1", "ps-properties-l2",
                "ps-yaml-l3", "base-properties-l4-v2", "base-yaml-l5-v2");
        assertChangeCount(4);
        propertiesChangeMockListener.clear();


        // swap application-ps.yaml and application-ps.properties
        modifyFile("ps/hotswap/application-ps-bak.properties", "application-ps.properties");
        modifyFile("ps/hotswap/application-ps-bak.yaml", "application-ps.yaml");
        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 4);
            }
        }, 120000));
        test10Properties = applicationContext.getBean(Test10Properties.class);
        test80Properties = applicationContext.getBean(Test80Properties.class);
        test50Service = applicationContext.getBean(Test50Service.class);
        test80Service = applicationContext.getBean(Test80Service.class);
        assert10And50(test10Properties, test50Service, "test-properties-world-v2", "ps-properties-greeting-v2",
                "test-properties-geek-v2", "base-properties-boot-v2", 8002, "app-properties-l10-v2", "test-properties-l11-v2",
                "test-properties-l12-v2");
        assert80(test80Properties, test80Service, "ps-properties-l1-v2", "ps-properties-l2-v2",
                "ps-yaml-l3-v2", "base-properties-l4-v2", "base-yaml-l5-v2");
        assertChangeCount(5);
        assertContainKey("properties.l10.l2", "properties.l50.l2", "properties.l80.l1", "properties.l80.l2", "properties.l80.l3");
        assertContent("properties.l10.l2", "ps-properties-greeting", "ps-properties-greeting-v2",
                "properties.l50.l2", "ps-properties-greeting", "ps-properties-greeting-v2",
                "properties.l80.l1", "ps-properties-l1", "ps-properties-l1-v2",
                "properties.l80.l2", "ps-properties-l2", "ps-properties-l2-v2",
                "properties.l80.l3", "ps-yaml-l3", "ps-yaml-l3-v2");
        propertiesChangeMockListener.clear();
    }

    private void assertChangeCount(int changeNum) {
        Assert.assertEquals(propertiesChangeMockListener.newValueMap().size(), changeNum);
        Assert.assertEquals(propertiesChangeMockListener.oldValueMap().size(), changeNum);
    }

    private void assertContainKey(String... keys) {
        for (String key : keys) {
            Assert.assertTrue(propertiesChangeMockListener.newValueMap().containsKey(key));
            Assert.assertTrue(propertiesChangeMockListener.oldValueMap().containsKey(key));
        }
    }

    private void assertContent(String... keyValue) {
        for (int i = 0; i < keyValue.length; i++) {
            String key = keyValue[i];
            String oldValue = keyValue[++i];
            String newValue = keyValue[++i];
            Assert.assertEquals(propertiesChangeMockListener.newValueMap().get(key), newValue);
            Assert.assertEquals(propertiesChangeMockListener.oldValueMap().get(key), oldValue);
        }
    }

    private void assert10And50(Test10Properties test10Properties, Test50Service test50Service, int suffix, String suffixstr) {
        assertAllEquals(test10Properties.getL1(), test50Service.getL1(), "test-properties-world" + suffixstr);
        assertAllEquals(test10Properties.getL2(), test50Service.getL2(), "ps-properties-greeting" + suffixstr);
        assertAllEquals(test10Properties.getL3(), test50Service.getL3(), "test-properties-geek" + suffixstr);
        assertAllEquals(test10Properties.getL4(), test50Service.getL4(), "base-properties-boot" + suffixstr);
        assertAllEquals(test10Properties.getL5(), test50Service.getL5(), 8000 + suffix);
        assertAllEquals(test10Properties.getL10(), test50Service.getL10(), "app-properties-l10" + suffixstr);
        assertAllEquals(test10Properties.getL11(), test50Service.getL11(), "test-properties-l11" + suffixstr);
        assertAllEquals(test10Properties.getL12(), test50Service.getL12(), "test-properties-l12" + suffixstr);
    }

    private void assert80(Test80Properties test80Properties, Test80Service test80Service, String l1, String l2,
                          String l3, String l4, String l5) {
        assertAllEquals(test80Properties.getL1(), test80Service.getL1(), l1);
        assertAllEquals(test80Properties.getL2(), test80Service.getL2(), l2);
        assertAllEquals(test80Properties.getL3(), test80Service.getL3(), l3);
        assertAllEquals(test80Properties.getL4(), test80Service.getL4(), l4);
        assertAllEquals(test80Properties.getL5(), test80Service.getL5(), l5);
    }

    private void assert10And50(Test10Properties test10Properties, Test50Service test50Service, String l1, String l2,
                               String l3, String l4, int l5, String l10, String l11, String l12) {
        assertAllEquals(test10Properties.getL1(), test50Service.getL1(), l1);
        assertAllEquals(test10Properties.getL2(), test50Service.getL2(), l2);
        assertAllEquals(test10Properties.getL3(), test50Service.getL3(), l3);
        assertAllEquals(test10Properties.getL4(), test50Service.getL4(), l4);
        assertAllEquals(test10Properties.getL5(), test50Service.getL5(), l5);
        assertAllEquals(test10Properties.getL10(), test50Service.getL10(), l10);
        assertAllEquals(test10Properties.getL11(), test50Service.getL11(), l11);
        assertAllEquals(test10Properties.getL12(), test50Service.getL12(), l12);
    }

    private void assertAllEquals(Object o1, Object o2, Object target) {
        Assert.assertEquals(o1, o2);
        Assert.assertEquals(o1, target);
    }

    private void printObject(Test10Properties test10Properties, Test50Service test50Service,
                             Test80Properties test80Properties, Test80Service test80Service) {
        System.out.println(test10Properties.getL1());
        System.out.println(test10Properties.getL2());
        System.out.println(test10Properties.getL3());
        System.out.println(test10Properties.getL4());
        System.out.println(test10Properties.getL5());
        System.out.println(test10Properties.getL10());
        System.out.println(test10Properties.getL11());
        System.out.println(test10Properties.getL12());
        System.out.println("-----");
        System.out.println(test50Service.getL1());
        System.out.println(test50Service.getL2());
        System.out.println(test50Service.getL3());
        System.out.println(test50Service.getL4());
        System.out.println(test50Service.getL5());
        System.out.println(test50Service.getL10());
        System.out.println(test50Service.getL11());
        System.out.println(test50Service.getL12());
        System.out.println("-----");
        System.out.println(test80Properties.getL1());
        System.out.println(test80Properties.getL2());
        System.out.println(test80Properties.getL3());
        System.out.println(test80Properties.getL4());
        System.out.println(test80Properties.getL5());
        System.out.println("-----");
        System.out.println(test80Service.getL1());
        System.out.println(test80Service.getL2());
        System.out.println(test80Service.getL3());
        System.out.println(test80Service.getL4());
        System.out.println(test80Service.getL5());
    }

    private void modifyFile(String src, String target) throws Exception {
        org.springframework.core.io.Resource propertyFile = new ClassPathResource(target);
        org.springframework.core.io.Resource changedPropertyFile = new ClassPathResource(src);
        Files.copy(changedPropertyFile.getFile().toPath(), propertyFile.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
