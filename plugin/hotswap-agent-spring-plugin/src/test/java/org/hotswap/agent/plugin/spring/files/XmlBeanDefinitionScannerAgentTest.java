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
import org.hotswap.agent.plugin.spring.utils.ResourceUtils;
import org.junit.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.UrlResource;

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

        List<String> warnings = warningsLoggedBy(() -> XmlBeanDefinitionScannerAgent.registerBean("personRepository", beanDefinition));

        assertEquals(new ArrayList<String>(), warnings);
    }

    /**
     * Spring Security 6.2 registers bean definitions whose resource is a configuration class in its jar (#581).
     */
    @Test
    public void registerBeanWithClassInJarDoesNotWarn() throws Exception {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(Object.class);
        beanDefinition.setResource(new UrlResource("jar:file:/repo/spring-security-config-6.2.4.jar!/org/springframework/"
                + "security/config/annotation/method/configuration/PrePostMethodSecurityConfiguration.class"));

        List<String> warnings = warningsLoggedBy(() -> XmlBeanDefinitionScannerAgent.registerBean("preFilterAuthorizationMethodInterceptor", beanDefinition));

        assertEquals(new ArrayList<String>(), warnings);
    }

    /**
     * An XML file in an unknown output folder is matched by its file, so the path is used as it is (#397).
     */
    @Test
    public void xmlFileInUnknownOutputFolderDoesNotWarn() {
        String path = "/project/build/resources/main/applicationContext.xml";

        List<String> warnings = warningsLoggedBy(() -> assertEquals(path, ResourceUtils.convertToClasspathURL(path)));

        assertEquals(new ArrayList<String>(), warnings);
    }

    private static List<String> warningsLoggedBy(Runnable action) {
        // ResourceUtils logs with the logger of XmlBeanDefinitionScannerAgent, make sure warnings are enabled
        AgentLogger.setLevel(XmlBeanDefinitionScannerAgent.class.getName(), AgentLogger.Level.TRACE);
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
            action.run();
        } finally {
            AgentLogger.setHandler(originalHandler);
        }
        return warnings;
    }
}
