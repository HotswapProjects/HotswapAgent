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
package org.hotswap.agent.plugin.spring.files;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLoggerHandler;
import org.junit.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class XmlBeanDefinitionScannerAgentTest {

    /**
     * Spring Data describes the source of a repository bean definition only by text
     * ("... defined in @EnableJpaRepositories declared on ..."), there is no XML file to find an agent for.
     */
    @Test
    public void registerBeanWithResourceDescriptionDoesNotWarn() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(Object.class);
        beanDefinition.setResourceDescription(
                "app.PersonRepository defined in @EnableJpaRepositories declared on DatabaseConfiguration");

        List<String> warnings = new ArrayList<>();
        AgentLoggerHandler originalHandler = AgentLogger.getHandler();
        AgentLogger.setHandler(new AgentLoggerHandler() {
            @Override
            public void print(Class clazz, AgentLogger.Level level, String message, Throwable throwable, Object... args) {
                if (level == AgentLogger.Level.WARNING || level == AgentLogger.Level.ERROR) {
                    warnings.add(message);
                }
                super.print(clazz, level, message, throwable, args);
            }
        });
        try {
            XmlBeanDefinitionScannerAgent.registerBean("personRepository", beanDefinition);
        } finally {
            AgentLogger.setHandler(originalHandler);
        }

        assertEquals(new ArrayList<String>(), warnings);
    }
}
