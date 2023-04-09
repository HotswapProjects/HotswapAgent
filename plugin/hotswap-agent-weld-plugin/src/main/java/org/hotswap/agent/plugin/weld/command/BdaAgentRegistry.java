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
package org.hotswap.agent.plugin.weld.command;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BdaAgentRegistry - maps archivePath to BeanClassRefreshAgent. This class is separated from BeanClassRefreshAgent
 * to avoid "class not found exception" when map is accessed from ClassPathBeanRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class BdaAgentRegistry {

    // map archive path -> BeanClassRefreshAgent
    private static Map<String, BeanClassRefreshAgent> INSTANCES = new ConcurrentHashMap<>();

    public static Map<String, BeanClassRefreshAgent> getInstances() {
        return INSTANCES;
    }

    public static boolean contains(String archivePath) {
        return INSTANCES.containsKey(archivePath);
    }

    public static void put(String archivePath, BeanClassRefreshAgent bdaAgent) {
        INSTANCES.put(archivePath, bdaAgent);
    }

    public static BeanClassRefreshAgent get(String archivePath) {
        return INSTANCES.get(archivePath);
    }

    public static Collection<BeanClassRefreshAgent> values() {
        return INSTANCES.values();
    }

    /**
     * Iterate over agents and find the one containing the class by name
     *
     * @param className
     * @return
     */
    public static String getArchiveByClassName(String className){
        for(BeanClassRefreshAgent agent: INSTANCES.values()) {
            if(agent.getDeploymentArchive().getBeanClasses().contains(className)) {
                return agent.getArchivePath();
            }
        }
        return null;
    }
}
