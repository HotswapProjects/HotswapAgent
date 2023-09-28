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
package org.hotswap.agent.plugin.spring.xml.placeholder;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:xml-placeholder/placeholderContext.xml"})
public class PlaceholderTest {
    @Autowired
    private AbstractApplicationContext applicationContext;

    private static final Resource propertyFile = new ClassPathResource("xml-placeholder/item.properties");
    private static final Resource changedPropertyFile = new ClassPathResource("xml-placeholder/itemChanged.properties");

    @Test
    public void swapPropertyTest() throws Exception {
        BaseTestUtil.configMaxReloadTimes();
        System.out.println("PlaceholderTest.swapPropertyTest" + applicationContext.getBeanFactory());
        Item1 item1 = applicationContext.getBean("item1", Item1.class);
        Item1 item11 = applicationContext.getBean("item11", Item1.class);
        Item2 item2 = applicationContext.getBean("item2", Item2.class);
        Item2 item22 = applicationContext.getBean("item22", Item2.class);
        Item3 item3 = applicationContext.getBean("item3", Item3.class);
        Item4 item4 = applicationContext.getBean("item4", Item4.class);
        Item5 item5 = applicationContext.getBean("item5", Item5.class);
        assertEquals("item-name", item1.getName());
        assertEquals("item-name", item2.getName());
        assertEquals("item-name", item11.getName());
        assertEquals("item-name", item22.getName());
        assertEquals("item-name", item3.getName());
        assertEquals("item4-name", item4.getName());
        assertEquals("item-name", item5.getName());
        assertEquals("item5-name", item5.getName2());

        byte[] content = Files.readAllBytes(propertyFile.getFile().toPath());
        try {
            modifyPropertyFile();
            Thread.sleep(10000);

            Item1 itemChange1 = applicationContext.getBean("item1", Item1.class);
            Item1 itemChange11 = applicationContext.getBean("item11", Item1.class);
            Item2 itemChange2 = applicationContext.getBean("item2", Item2.class);
            Item2 itemChange22 = applicationContext.getBean("item22", Item2.class);
            Item3 itemChange3 = applicationContext.getBean("item3", Item3.class);
            Item4 itemChange4 = applicationContext.getBean("item4", Item4.class);
            Item5 itemChange5 = applicationContext.getBean("item5", Item5.class);

            assertEquals("ITEM-NAME", itemChange1.getName());
            assertEquals("ITEM-NAME", itemChange2.getName());
            assertEquals("ITEM-NAME", itemChange11.getName());
            assertEquals("ITEM-NAME", itemChange22.getName());
            assertEquals("ITEM-NAME", itemChange3.getName());
            assertEquals("ITEM4-NAME", itemChange4.getName());
            assertEquals("ITEM-NAME", itemChange5.getName());
            assertEquals("ITEM5-NAME", itemChange5.getName2());
            assertNotEquals(item1, itemChange1);
            assertNotEquals(item11, itemChange11);
            assertNotEquals(item4, itemChange4);
            assertNotEquals(item5, itemChange5);
            assertNotEquals(item2, itemChange2);
            assertNotEquals(item22, itemChange22);
            assertEquals(item3, itemChange3);

            // part 2
            assertNotNull(applicationContext.getBean("item2", Item2.class).getName());
            assertNull(applicationContext.getBean("item2", Item2.class).getName2());
            HotSwapper.swapClasses(Item2.class, Item2WithoutValue.class.getName());
            Thread.sleep(10000);

            assertNull(applicationContext.getBean("item2", Item2.class).getName());
            assertNotNull(applicationContext.getBean("item2", Item2.class).getName2());
        } finally {
            Files.write(propertyFile.getFile().toPath(), content);
        }
    }

    private void modifyPropertyFile() throws Exception {
        Files.copy(changedPropertyFile.getFile().toPath(), propertyFile.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
