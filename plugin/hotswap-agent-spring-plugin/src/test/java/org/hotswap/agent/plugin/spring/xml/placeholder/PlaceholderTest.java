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

import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:placeholderContext.xml"})
public class PlaceholderTest {
    @Autowired
    private ApplicationContext applicationContext;

    private static final Resource propertyFile = new ClassPathResource("item.properties");
    private static final Resource changedPropertyFile = new ClassPathResource("itemChanged.properties");

    @Test
    public void swapPropertyTest() throws Exception {
        Assert.assertEquals("item-name", applicationContext.getBean("item1", Item1.class).getName());
        Assert.assertEquals("item-name", applicationContext.getBean("item2", Item2.class).getName());

        XmlBeanDefinitionScannerAgent.reloadFlag = true;
        modifyPropertyFile();

        Assert.assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !XmlBeanDefinitionScannerAgent.reloadFlag;
            }
        }, 5000));

        Assert.assertEquals("ITEM-NAME", applicationContext.getBean("item1", Item1.class).getName());
        Assert.assertEquals("ITEM-NAME", applicationContext.getBean("item2", Item2.class).getName());
    }


    private void modifyPropertyFile() throws Exception {
        Files.copy(changedPropertyFile.getFile().toPath(), propertyFile.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
