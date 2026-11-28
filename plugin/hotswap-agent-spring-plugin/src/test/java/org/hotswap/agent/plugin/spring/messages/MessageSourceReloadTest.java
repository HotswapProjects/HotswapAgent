/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A changed message bundle is read again by the message sources of the context.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MessageSourceConfiguration.class)
public class MessageSourceReloadTest {
    @Autowired
    private AbstractApplicationContext applicationContext;

    @Before
    public void before() {
        BaseTestUtil.configMaxReloadTimes();
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) applicationContext.getBeanFactory());
        BeanFactoryAssistant.getBeanFactoryAssistant(applicationContext.getBeanFactory()).reset();
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) applicationContext.getBeanFactory());
        BeanFactoryAssistant.getBeanFactoryAssistant(applicationContext.getBeanFactory()).reset();
    }

    @Test
    public void changedMessageBundlesAreReadAgain() throws Exception {
        // the messages are cached now
        assertEquals("Hello", applicationContext.getMessage("greeting", null, Locale.ENGLISH));
        assertEquals("Goodbye", applicationContext.getMessage("farewell", null, Locale.ENGLISH));

        Path bundle = new ClassPathResource("messages/bundle_en.properties").getFile().toPath();
        Path reloadable = new ClassPathResource("messages/reloadable_en.properties").getFile().toPath();
        byte[] bundleContent = Files.readAllBytes(bundle);
        byte[] reloadableContent = Files.readAllBytes(reloadable);
        try {
            Files.write(bundle, "greeting=Hello again\n".getBytes(StandardCharsets.UTF_8));
            Files.write(reloadable, "farewell=Goodbye again\n".getBytes(StandardCharsets.UTF_8));
            assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
                @Override
                public boolean result() throws Exception {
                    return BaseTestUtil.finishReloading(applicationContext.getBeanFactory(), 1);
                }
            }, 11000));

            assertEquals("Hello again", applicationContext.getMessage("greeting", null, Locale.ENGLISH));
            assertEquals("Goodbye again", applicationContext.getMessage("farewell", null, Locale.ENGLISH));
        } finally {
            Files.write(bundle, bundleContent);
            Files.write(reloadable, reloadableContent);
        }
    }
}
