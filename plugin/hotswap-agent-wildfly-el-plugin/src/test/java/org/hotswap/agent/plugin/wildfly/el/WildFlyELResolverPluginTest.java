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
package org.hotswap.agent.plugin.wildfly.el;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import jakarta.el.ELProcessor;
import jakarta.el.PropertyNotFoundException;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.wildfly.el.testBeans.Item;
import org.hotswap.agent.plugin.wildfly.el.testBeansHotswap.Item2;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;

/**
 * Tests that a property added by hotswap is resolved by jakarta.el.BeanELResolver of WildFly
 * (jboss-el-api_5.0_spec), which caches the bean properties in org.jboss.el.cache.BeanPropertiesCache.
 */
public class WildFlyELResolverPluginTest {

    @Test
    public void newPropertyResolvedAfterHotswap() throws Exception {
        assertEquals("item", eval("item.name"));
        try {
            eval("item.tags");
            fail("Property tags should not exist before hotswap");
        } catch (PropertyNotFoundException expected) {
            // the properties of Item are cached now
        }

        HotSwapper.swapClasses(Item.class, Item2.class.getName());
        try {
            assertTrue("Property tags resolved after hotswap", WaitHelper.waitForCommand(new WaitHelper.Command() {
                @Override
                public boolean result() {
                    try {
                        return "tag1,tag2".equals(eval("item.tags"));
                    } catch (PropertyNotFoundException e) {
                        return false;
                    }
                }
            }, 5000));
        } finally {
            HotSwapper.swapClasses(Item.class, Item.class.getName());
        }
    }

    private static Object eval(String expression) {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("item", new Item());
        return processor.eval(expression);
    }
}
